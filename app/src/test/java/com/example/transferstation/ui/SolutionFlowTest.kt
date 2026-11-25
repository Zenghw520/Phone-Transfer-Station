package com.example.transferstation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证解题流程解析能区分说明、标题、公式和结论。 */
class SolutionFlowTest {
    /** 验证常见数学回答可被组织为图形化步骤。 */
    @Test
    fun parseSolutionFlowSeparatesStepsEquationsAndAnswer() {
        val answer = "设 A、B 为矩阵。\n\n要求\n\n\\[\\det(A)=3\\]\n\n所以答案是 \\boxed{3}"

        val blocks = parseSolutionFlow(answer)

        assertEquals(
            listOf(
                SolutionBlockStyle.STEP,
                SolutionBlockStyle.HEADING,
                SolutionBlockStyle.EQUATION,
                SolutionBlockStyle.ANSWER,
            ),
            blocks.map(SolutionBlock::style),
        )
    }
}
