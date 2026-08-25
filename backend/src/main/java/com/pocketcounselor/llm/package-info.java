/**
 * LLM provider abstraction: the {@link com.pocketcounselor.llm.LlmClient} interface,
 * one implementation per vendor wire format, and the rate-limiting decorator.
 *
 * <p>Nothing in this package knows about scoring, prompts, or JSON schemas -- it
 * turns a prompt string into raw model text and maps vendor error envelopes onto
 * {@link com.pocketcounselor.llm.LlmException}.
 */
package com.pocketcounselor.llm;
