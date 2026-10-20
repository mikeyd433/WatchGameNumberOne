package com.mikeyd.bezeltown.render

/**
 * A 3x5 pixel font for signs built out of little 3D blocks. Small enough that
 * a whole building name fits on one line, big enough to stay readable through
 * the low-resolution PS1 pass. Lowercase is drawn as uppercase.
 */
object PixelFont {
    const val HEIGHT = 5
    const val SPACING = 1

    private val glyphs: Map<Char, List<String>> = mapOf(
        'A' to listOf(".#.", "#.#", "###", "#.#", "#.#"),
        'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
        'C' to listOf(".##", "#..", "#..", "#..", ".##"),
        'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
        'E' to listOf("###", "#..", "##.", "#..", "###"),
        'F' to listOf("###", "#..", "##.", "#..", "#.."),
        'G' to listOf(".##", "#..", "#.#", "#.#", ".##"),
        'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"),
        'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
        'J' to listOf("..#", "..#", "..#", "#.#", ".#."),
        'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
        'L' to listOf("#..", "#..", "#..", "#..", "###"),
        'M' to listOf("#.#", "###", "###", "#.#", "#.#"),
        'N' to listOf("##.", "#.#", "#.#", "#.#", "#.#"),
        'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."),
        'P' to listOf("##.", "#.#", "##.", "#..", "#.."),
        'Q' to listOf(".#.", "#.#", "#.#", "##.", ".##"),
        'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
        'S' to listOf(".##", "#..", ".#.", "..#", "##."),
        'T' to listOf("###", ".#.", ".#.", ".#.", ".#."),
        'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
        'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."),
        'W' to listOf("#.#", "#.#", "###", "###", "#.#"),
        'X' to listOf("#.#", "#.#", ".#.", "#.#", "#.#"),
        'Y' to listOf("#.#", "#.#", ".#.", ".#.", ".#."),
        'Z' to listOf("###", "..#", ".#.", "#..", "###"),
        '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
        '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
        '2' to listOf("##.", "..#", ".#.", "#..", "###"),
        '3' to listOf("##.", "..#", ".#.", "..#", "##."),
        '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
        '5' to listOf("###", "#..", "##.", "..#", "##."),
        '6' to listOf(".##", "#..", "###", "#.#", "###"),
        '7' to listOf("###", "..#", ".#.", ".#.", ".#."),
        '8' to listOf("###", "#.#", "###", "#.#", "###"),
        '9' to listOf("###", "#.#", "###", "..#", "##."),
        '$' to listOf(".#.", "##.", ".#.", ".##", ".#."),
        '.' to listOf(".", ".", ".", ".", "#"),
        ' ' to listOf("..", "..", "..", "..", ".."),
    )

    private fun glyph(c: Char) = glyphs[c.uppercaseChar()] ?: glyphs.getValue(' ')

    /** Width of [text] in font pixels. */
    fun width(text: String): Int =
        if (text.isEmpty()) 0 else text.sumOf { glyph(it)[0].length } + SPACING * (text.length - 1)

    /** Calls [pixel] with (x, y) for every lit pixel, x right from 0 and y down from 0. */
    fun forEachPixel(text: String, pixel: (x: Int, y: Int) -> Unit) {
        var penX = 0
        for (c in text) {
            val rows = glyph(c)
            for (y in rows.indices) for (x in rows[y].indices) {
                if (rows[y][x] == '#') pixel(penX + x, y)
            }
            penX += rows[0].length + SPACING
        }
    }
}
