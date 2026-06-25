/*
 * Copyright (c) 2026 Afterglow contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package com.growse.android.io.github.hidroh.materialistic.ai

import com.growse.android.io.github.hidroh.materialistic.HackerNews
import com.growse.android.io.github.hidroh.materialistic.IoDispatcher
import com.growse.android.io.github.hidroh.materialistic.data.Item
import com.growse.android.io.github.hidroh.materialistic.data.ItemManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * The result of flattening a comment thread into LLM-ready text.
 *
 * [text] is the formatted body sent to the summarizer. [includedCount] (N) is how many live
 * comments we actually included; [totalDescendants] (M) is the thread's total comment count (the
 * story's descendants count, or the number we encountered if HN does not report one). [truncated]
 * is true when the cost cap stopped us before we drained the tree, OR a child fetch failed and we
 * skipped its subtree (a best-effort gap is surfaced, not hidden).
 */
data class AssembledThread(
    val text: String,
    val includedCount: Int,
    val totalDescendants: Int,
    val truncated: Boolean,
) {
  // Human-facing disclosure string for the UI (D6: no silent truncation). G4 renders this.
  val disclosure: String
    get() =
        if (truncated) "Summarized the top $includedCount of $totalDescendants comments"
        else "Summarized all $includedCount comments"
}

/**
 * Flattens an HN thread into a single rank-ordered, indented text block for the summarizer.
 *
 * Mechanism: a breadth-first walk of the comment tree starting at the root's kids. Each BFS chunk
 * of [CONCURRENCY] ids is fetched in parallel (async { itemManager.getItem(...) } + awaitAll) to
 * bound in-flight network reads, then the results are consumed IN array order so the emitted text
 * keeps HN rank order. Uses the HackerNews-backed [ItemManager] (not Algolia) so kids / dead /
 * deleted come from the canonical Firebase payload, mirroring
 * [com.growse.android.io.github.hidroh.materialistic .reply.HackerNewsReplyDataSource]. Cost-capped
 * per D6 so we never silently overrun the model context.
 */
@Singleton
class ThreadTextAssembler
@Inject
constructor(
    @HackerNews private val itemManager: ItemManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

  suspend fun assemble(rootItemId: String): AssembledThread =
      withContext(ioDispatcher) {
        // A null root is a transient fetch failure (getItem returns null on IOException), not an
        // empty thread, mirroring the reply source. Fail loud so the caller can retry/surface it.
        val root =
            itemManager.getItem(rootItemId, ItemManager.MODE_DEFAULT)
                ?: throw ThreadAssemblyException("thread fetch failed: $rootItemId")

        // M: total comment count if HN reports it; -1 means unknown (a comment-rooted thread).
        val reportedDescendants = root.descendants.takeIf { it >= 0 }

        val header = buildHeader(root)
        val lines = StringBuilder()
        var includedCount = 0
        var bodyChars = 0
        var truncated = false
        var hadFetchGap = false

        // FIFO of (id, depth) pairs. Depth drives indentation; we track it ourselves rather than
        // trusting getLevel() so a comment-rooted thread starts its kids at depth 0.
        val queue = ArrayDeque<Pair<String, Int>>()
        root.kids?.forEach { queue.addLast(it.toString() to 0) }

        bfs@ while (queue.isNotEmpty()) {
          // Take up to CONCURRENCY ids, fetch them in parallel, then handle results in order.
          val chunk = ArrayList<Pair<String, Int>>(CONCURRENCY)
          while (chunk.size < CONCURRENCY && queue.isNotEmpty()) {
            chunk.add(queue.removeFirst())
          }

          val fetched = coroutineScope {
            chunk
                .map { (id, depth) ->
                  async { id to (itemManager.getItem(id, ItemManager.MODE_DEFAULT) to depth) }
                }
                .awaitAll()
          }

          for ((id, itemAndDepth) in fetched) {
            val (item, depth) = itemAndDepth
            if (item == null) {
              // A single flaky child should not throw away a best-effort summary, so (unlike
              // HackerNewsReplyDataSource, which must never drop a kid) we skip this node and its
              // subtree and fold the gap into `truncated` (surfaced, not silent).
              hadFetchGap = true
              continue
            }
            // A dead/deleted node hides its own text but may still have live children HN shows, so
            // we enqueue its kids even though we do not emit a line for it.
            if (!item.isDeleted && !item.isDead) {
              val body = item.displayedText?.toString()?.trim()
              if (!body.isNullOrBlank()) {
                // depth*2 spaces of indentation visually encodes the reply nesting for the model.
                val line = " ".repeat(depth * 2) + "[" + (item.by ?: "unknown") + "] " + body
                lines.append(line).append('\n')
                includedCount++
                bodyChars += line.length
                // Cost caps (D6): stop INCLUDING and stop FETCHING once either ceiling is hit, so
                // we neither overrun the model context nor keep paying for reads we will discard.
                if (includedCount >= MAX_COMMENTS || bodyChars >= MAX_CHARS) {
                  truncated = true
                  break@bfs
                }
              }
            }
            item.kids?.forEach { queue.addLast(it.toString() to depth + 1) }
          }
        }

        if (hadFetchGap) truncated = true
        // Best estimate when HN does not report descendants: what we actually saw.
        val totalDescendants = reportedDescendants ?: includedCount
        val text = if (lines.isEmpty()) header else header + "\n\n" + lines.toString().trimEnd('\n')
        AssembledThread(
            text = text,
            includedCount = includedCount,
            totalDescendants = totalDescendants,
            truncated = truncated,
        )
      }

  /**
   * Header from the root: the story title if present, plus the root body (Ask HN / self-post) when
   * its rendered text is non-blank. Comments under it follow in the body.
   */
  private fun buildHeader(root: Item): String {
    val parts = ArrayList<String>(2)
    root.title?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    root.displayedText?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    return parts.joinToString("\n\n")
  }

  companion object {
    // CONCURRENCY: max in-flight getItem fetches per BFS chunk. Ceiling = HN_FETCH_FANOUT; raise if
    // assembly latency dominates and the HN endpoint tolerates more parallel reads.
    const val CONCURRENCY = 6

    // MAX_COMMENTS: max live comments included. Ceiling = LLM_COMMENT_BUDGET; raise alongside the
    // provider's max_tokens if summaries start missing late-thread context.
    const val MAX_COMMENTS = 60

    // MAX_CHARS: max accumulated body length. Ceiling = LLM_INPUT_CHAR_BUDGET, kept well under the
    // selected provider's context window (each provider sets its own output token limit). Raise in
    // lockstep with the chosen model's context window.
    const val MAX_CHARS = 48_000
  }
}

/**
 * A genuine HN fetch failure for the thread root. Mirrors ReplyFetchException's fail-loud intent.
 */
class ThreadAssemblyException(message: String) : Exception(message)
