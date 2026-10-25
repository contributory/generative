package org.starfall.generative.home.chat.logic.markdown


object LatexDetector {

    private val blockDollarExpression = Regex("""(?s)^\s*\$\$(.+?)\$\$\s*$""")
    private val bracketExpression = Regex("""(?s)^\s*\\\[(.+?)\\\]\s*$""")
    private val parenthesizedExpression = Regex("""(?s)^\s*\\\((.+?)\\\)\s*$""")
    private val pureArithmetic = Regex("""[\d\s.,+\-−–*/×÷=<>≠≤≥±()]+""")
    private val arithmeticToken = Regex("""\d+(?:[.,]\d+)?|[+\-−–*/×÷=<>≠≤≥±]|[()]|\S""")
    private val singleDollarExpression = Regex("""(?s)(?<!\$)\$([^\$\n]+?)\$(?!\$)""")

    /**
     * Determines whether to render LaTeX based on the user's preference mode
     * ("OFF" or "AUTO"; a legacy "ON" behaves like "AUTO").
     */
    fun shouldRenderLatex(text: String, mode: String): Boolean {
        return !mode.equals("OFF", ignoreCase = true) && containsLatex(text)
    }

    /**
     * Intelligent auto-detection algorithm that distinguishes between LaTeX math equations
     * and USD price ranges (e.g., "$10 to $20", "$10 - $20", "$10.50 - $19.99").
     */
    fun containsLatex(text: String): Boolean {
        if (text.isBlank()) return false

        // 1. Double dollar $$ ... $$ is LaTeX block math.
        if (Regex("""(?s)\$\$(.+?)\$\$""").containsMatchIn(text)) {
            return true
        }

        // 2. Escaped LaTeX delimiters like \( ... \) or \[ ... \]. An opening
        // delimiter by itself is common in streaming messages and is not math yet.
        if (containsPairedExpression(text, "\\(", "\\)") ||
            containsPairedExpression(text, "\\[", "\\]")) {
            return true
        }

        // 3. Find single-dollar blocks: $ ... $
        val matches = singleDollarExpression.findAll(text).toList()

        if (matches.isEmpty()) return false

        // Check each match to see if it resembles a LaTeX expression
        for (match in matches) {
            val inner = match.groupValues[1].trim()

            // Check if this match is part of a USD price range pattern:
            if (isUsdPriceRange(text, match)) {
                continue // Skip price ranges like "$10 to $20", "$10 - $20"
            }

            if (looksLikeMath(inner)) {
                return true
            }
        }

        return false
    }

    /**
     * Returns a formula only when the whole paragraph is one math expression.
     * Paragraphs that mix prose and math are cut up by [MathSplitter] instead.
     */
    internal fun standaloneFormula(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        blockDollarExpression.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }
        bracketExpression.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }
        parenthesizedExpression.matchEntire(trimmed)?.let { return it.groupValues[1].trim() }

        singleDollarExpression.matchEntire(trimmed)?.let { match ->
            val inner = match.groupValues[1].trim()
            if (looksLikeMath(inner) && !isUsdPriceRange(trimmed, match)) return inner
        }
        return null
    }

    private fun containsPairedExpression(text: String, opening: String, closing: String): Boolean {
        var start = text.indexOf(opening)
        while (start >= 0) {
            val end = text.indexOf(closing, start + opening.length)
            if (end > start + opening.length && looksLikeMath(text.substring(start + opening.length, end).trim())) {
                return true
            }
            start = text.indexOf(opening, start + opening.length)
        }
        return false
    }

    internal fun looksLikeMath(expression: String): Boolean {
        val inner = expression.trim()
        if (inner.isEmpty()) return false

        // LaTeX commands (e.g. \frac, \alpha, \sqrt, \int, \sum).
        if (inner.contains("\\")) return true

        // Superscript and subscript notation is unambiguous in a delimited block.
        if (inner.contains("^") || inner.contains("_")) return true

        // Common math functions (e.g. sin, cos, log, lim, sqrt, sum, int).
        if (inner.contains(Regex("""\b(sin|cos|tan|log|ln|lim|sqrt|sum|int|f\s*\([a-zA-Z]\))\b"""))) {
            return true
        }

        // Mathematical operations with algebraic variables: x + y, a = b, 2x + 3.
        if (inner.contains(Regex("""[a-zA-Z]\s*[+\-*/=<>±≠≤≥]\s*[a-zA-Z0-9]""")) ||
            inner.contains(Regex("""[0-9]\s*[+\-*/=<>±≠≤≥]\s*[a-zA-Z]"""))) {
            return true
        }

        // Simple arithmetic on numbers (2 + 3 = 5, 2 / 3, 2 - 3 = 5) must be a well-formed expression.
        arithmeticVerdict(inner)?.let { return it }

        // Single letter math variables such as $x$, $y$, or $N$.
        return inner.length == 1 && inner[0].isLetter()
    }

    /**
     * null when [inner] is not made only of numbers and arithmetic symbols. Otherwise whether it is a valid
     * expression: operands and operators alternate (so "10-" and "2 -" fail), parentheses balance, there is at
     * least one operator or sign ("-8", "+9"), and a binary minus has spaces on both sides ("2-5" is a range, not a subtraction).
     */
    private fun arithmeticVerdict(inner: String): Boolean? {
        if (!pureArithmetic.matches(inner) || inner.none { it.isDigit() }) return null
        val parser = ArithmeticParser(inner, arithmeticToken.findAll(inner).toList())
        return parser.expression() && parser.atEnd() && parser.operators + parser.signs > 0
    }

    private class ArithmeticParser(private val source: String, private val tokens: List<MatchResult>) {
        private var pos = 0
        var operators = 0
            private set
        var signs = 0
            private set

        fun atEnd() = pos == tokens.size

        private fun peek() = tokens.getOrNull(pos)?.value

        private fun isOperator(t: String?) = t != null && t.length == 1 && t[0] in "+-−–*/×÷=<>≠≤≥±"

        private fun operand(): Boolean {
            var t = peek() ?: return false
            // A sign glued to its operand: "-8", "+9", "2 * -3", "-(1 + 2)".
            if (t[0] in "-−–+") {
                val minus = tokens[pos]
                val next = tokens.getOrNull(pos + 1)
                if (next == null || next.range.first != minus.range.last + 1) return false
                if (!next.value[0].isDigit() && next.value != "(") return false
                signs++
                pos++
                t = next.value
            }
            if (t[0].isDigit()) { pos++; return true }
            if (t != "(") return false
            pos++
            if (!expression() || peek() != ")") return false
            pos++
            return true
        }

        fun expression(): Boolean {
            if (!operand()) return false
            while (isOperator(peek())) {
                val token = tokens[pos]
                if (token.value[0] in "-−–") {
                    val before = source.getOrNull(token.range.first - 1)
                    val after = source.getOrNull(token.range.last + 1)
                    if (before?.isWhitespace() != true || after?.isWhitespace() != true) return false
                }
                operators++
                pos++
                if (!operand()) return false
            }
            return true
        }
    }

    private fun isUsdPriceRange(fullText: String, match: MatchResult): Boolean =
        looksLikeUsdRange(match.groupValues[1].trim(), fullText.substring(match.range.last + 1))

    /** [inner] is the text between two dollar signs, [textAfterClose] what follows the second one. */
    internal fun looksLikeUsdRange(inner: String, textAfterClose: String): Boolean {
        // 1. If inner starts with a price number (e.g. "10", "10.50", "1,000")
        val startsWithNumber = inner.matches(Regex("""^\d+([.,]\d+)?.*""", RegexOption.DOT_MATCHES_ALL))
        if (!startsWithNumber) return false

        // Check if inner ends with common price range connectors like "to", "-", "and", "~", ","
        if (inner.endsWith("to", ignoreCase = true) ||
            inner.endsWith("-") ||
            inner.endsWith("and", ignoreCase = true) ||
            inner.endsWith("~") ||
            inner.endsWith(",")) {
            return true
        }

        // If what follows the second '$' starts with a number, "$10 ... $20" was matched.
        return textAfterClose.trimStart().matches(Regex("""^\d+([.,]\d+)?.*""", RegexOption.DOT_MATCHES_ALL))
    }
}
