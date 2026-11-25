package com.example.transferstation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证聊天消息中的常见数学标记转换。 */
class MathMarkupTest {
    /** 验证分数、框选结果和上标可以在 Android 正则引擎下正常解析。 */
    @Test
    fun renderMathMarkupConvertsCommonExpressions() {
        val input = "\\frac{a}{b} + \\boxed{x} + \\text{xy} + x^{2}"

        val result = renderMathMarkup(input)

        assertEquals("a⁄b + [x] + xy + x²", result)
    }

    /** 验证行列式算子和矩阵环境会转换为可读格式。 */
    @Test
    fun renderMathMarkupConvertsDeterminantAndMatrix() {
        val input = "\\det\\begin{vmatrix}a&b\\\\c&d\\end{vmatrix}"

        val result = renderMathMarkup(input)

        assertEquals("det|a   b|\n|c   d|", result)
    }

    /** 验证纯文本公式转换保留矩阵行内容，界面层负责绘制连续括号。 */
    @Test
    fun renderMathMarkupPreservesSquareMatrixRows() {
        val input = "\\begin{bmatrix}1\\\\2\\end{bmatrix},"

        val result = renderMathMarkup(input)

        assertEquals("[1]\n[2],", result)
    }
}
