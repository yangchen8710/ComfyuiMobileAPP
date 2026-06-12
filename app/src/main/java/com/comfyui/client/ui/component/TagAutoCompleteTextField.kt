package com.comfyui.client.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comfyui.client.data.model.DanbooruTag
import com.comfyui.client.data.repository.DanbooruRepository
import com.comfyui.client.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TagAutoCompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Enter Danbooru tags...",
) {
    val context = LocalContext.current
    val repository = remember { DanbooruRepository() }
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<DanbooruTag>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var currentWord by remember { mutableStateOf("") }
    var currentTagRange by remember { mutableStateOf(IntRange.EMPTY) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    LaunchedEffect(Unit) {
        repository.loadTags(context)
    }

    /**
     * Get the tag range at cursor position.
     * A tag is bounded by commas or newlines.
     */
    fun getCurrentTagRange(text: String, cursor: Int): IntRange? {
        if (text.isEmpty()) return null
        val pos = cursor.coerceIn(0, text.length)

        // Find start: after last comma/newline before or at cursor
        val before = text.substring(0, pos)
        val lastComma = before.lastIndexOf(',')
        val lastNewline = before.lastIndexOf('\n')
        val start = (maxOf(lastComma, lastNewline) + 1).coerceIn(0, text.length)

        // Find end: before next comma/newline after cursor
        val after = text.substring(pos)
        val nextComma = after.indexOf(',')
        val nextNewline = after.indexOf('\n')
        val endOffset = when {
            nextComma >= 0 && nextNewline >= 0 -> minOf(nextComma, nextNewline)
            nextComma >= 0 -> nextComma
            nextNewline >= 0 -> nextNewline
            else -> after.length
        }
        val end = (pos + endOffset).coerceIn(0, text.length)

        // Strip leading/trailing whitespace from boundaries
        var trimStart = start
        while (trimStart < end && text[trimStart].isWhitespace()) trimStart++
        var trimEnd = end
        while (trimEnd > trimStart && text[trimEnd - 1].isWhitespace()) trimEnd--

        if (trimStart >= trimEnd) return null
        return trimStart until trimEnd
    }

    /**
     * Get the partial tag being typed: text from tag start to cursor position.
     * Returns "" if no valid partial tag (e.g., cursor at start of tag, or escape sequence).
     */
    fun getCurrentPartialTag(text: String, cursor: Int): Pair<String, IntRange> {
        if (text.isEmpty()) return Pair("", IntRange.EMPTY)
        val safeCursor = cursor.coerceIn(0, text.length)

        val tagRange = getCurrentTagRange(text, safeCursor)
        if (tagRange == null || safeCursor <= tagRange.first) {
            return Pair("", IntRange.EMPTY)
        }

        // Extract from tag start to cursor
        val start = tagRange.first.coerceIn(0, text.length)
        val end = safeCursor.coerceIn(start, text.length)
        val partial = text.substring(start, end).trimStart()
        // Skip escape sequences (#, /)
        if (partial.startsWith("#") || partial.startsWith("/")) {
            return Pair("", tagRange)
        }

        // Skip if it's a weight modifier (e.g., :1.2)
        val lastColon = partial.lastIndexOf(':')
        if (lastColon >= 0) {
            val afterColon = partial.substring(lastColon + 1)
            if (afterColon.toFloatOrNull() != null && afterColon.toFloat() <= 9.9f) {
                return Pair("", tagRange)
            }
        }

        return Pair(partial.trim(), tagRange)
    }

    fun onTextChanged(newValue: TextFieldValue) {
        textFieldValue = newValue
        onValueChange(newValue.text)

        val (word, range) = getCurrentPartialTag(newValue.text, newValue.selection.start)
        currentWord = word
        currentTagRange = range

        debounceJob?.cancel()
        if (word.length >= 1) {
            debounceJob = scope.launch {
                delay(150)
                isLoading = true
                val results = repository.autocomplete(context, word)
                suggestions = results
                showSuggestions = results.isNotEmpty()
                isLoading = false
            }
        } else {
            showSuggestions = false
            suggestions = emptyList()
        }
    }

    /**
     * Insert the selected tag, replacing the partial tag at cursor.
     * Handles comma/space affixes properly.
     */
    fun insertTag(tag: String) {
        val text = textFieldValue.text
        val cursorPos = textFieldValue.selection.start.coerceIn(0, text.length)
        val range = currentTagRange

        // Determine tag boundaries (modeled after insertTagToTextArea in autocomplete.js)
        val tagStart: Int
        val tagEnd: Int
        if (range.isEmpty() || range.first >= text.length || range.last < 0) {
            // No tag at cursor - insert at cursor position
            tagStart = cursorPos
            tagEnd = cursorPos
        } else {
            tagStart = range.first.coerceIn(0, text.length)
            tagEnd = range.last.coerceIn(tagStart, text.length)
        }

        // Replace starts at min of cursor and tagStart (handles cursor before tag start)
        val replaceStart = minOf(cursorPos, tagStart).coerceIn(0, text.length)
        var replaceEnd = cursorPos.coerceIn(replaceStart, text.length)

        // Check if text after cursor is a suffix of the selected tag
        if (cursorPos < tagEnd && tagEnd <= text.length) {
            val textAfterCursor = text.substring(cursorPos, tagEnd).trimEnd()
            if (textAfterCursor.isNotEmpty() && textAfterCursor.length < tag.length &&
                tag.regionMatches(tag.length - textAfterCursor.length, textAfterCursor, 0, textAfterCursor.length, ignoreCase = true)) {
                replaceEnd = (cursorPos + textAfterCursor.length).coerceIn(replaceStart, text.length)
            }
        }

        // Prefix: add space if previous character is a comma
        val prefix = if (replaceStart > 0 && replaceStart <= text.length && text[replaceStart - 1] == ',') " " else ""

        // Suffix: add comma+space if next char isn't already a separator
        val needsSuffix = replaceEnd >= text.length || (replaceEnd < text.length && text[replaceEnd] !in ",:")
        val suffix = if (needsSuffix) ", " else ""

        val newText = text.substring(0, replaceStart) + prefix + tag + suffix +
                (if (replaceEnd < text.length) text.substring(replaceEnd) else "")
        val cursorEnd = (replaceStart + prefix.length + tag.length + suffix.length).coerceIn(0, newText.length)

        textFieldValue = TextFieldValue(newText, TextRange(cursorEnd))
        onValueChange(newText)
        showSuggestions = false
    }

    Box(modifier = modifier) {
        Column {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { onTextChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 14.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (showSuggestions && suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        items(suggestions) { tag ->
                            TagSuggestionRow(
                                tag = tag,
                                currentWord = currentWord,
                                onClick = { insertTag(tag.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagSuggestionRow(
    tag: DanbooruTag,
    currentWord: String,
    onClick: () -> Unit
) {
    val categoryColor = when (tag.category) {
        0 -> TagGeneral
        1 -> TagArtist
        3 -> TagCopyright
        4 -> TagCharacter
        5 -> TagMeta
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tagText = tag.name
        val highlightIndex = tagText.lowercase().indexOf(currentWord.lowercase())

        if (highlightIndex >= 0) {
            Text(text = tagText.substring(0, highlightIndex),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text(text = tagText.substring(highlightIndex, highlightIndex + currentWord.length),
                style = MaterialTheme.typography.bodyMedium,
                color = AccentCyan)
            Text(text = tagText.substring(highlightIndex + currentWord.length),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        } else {
            Text(text = tagText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(shape = RoundedCornerShape(4.dp), color = categoryColor.copy(alpha = 0.2f)) {
            Text(text = tag.categoryName,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = categoryColor)
        }

        Text(text = formatCount(tag.postCount),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
        count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
        else -> count.toString()
    }
}
