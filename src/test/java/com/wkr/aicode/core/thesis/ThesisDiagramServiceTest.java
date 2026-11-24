package com.wkr.aicode.core.thesis;

import cn.hutool.json.JSONUtil;
import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ThesisDiagramServiceTest {
    @TempDir Path temporary;

    @Test void storesTheSourceAndValidatedSvgAndKeepsOnlyOneCaption() throws Exception {
        ThesisTools tools = mock(ThesisTools.class);
        ThesisWriter writer = mock(ThesisWriter.class);
        when(tools.root()).thenReturn(temporary);
        when(tools.run(eq("diagram_design.py"), any(), anyInt(), eq("extract"), anyString()))
                .thenReturn("{\"type\":\"architecture\",\"nodes\":[]}");
        String svg = Files.readString(Path.of("tools/thesis/tests/sample_diagram.svg"));
        when(writer.drawDiagram(anyString(), anyString(), anyString(), anyString())).thenReturn(svg);
        ThesisDiagramService service = new ThesisDiagramService();
        ReflectionTestUtils.setField(service, "tools", tools);
        ReflectionTestUtils.setField(service, "writer", writer);
        String chapter = "正文\n\n```mermaid\nflowchart LR\nA[\"页面\"] --> B[\"接口\"]\n```\n\n图4.1 系统架构\n\n### 实现\n正文继续";
        String result = service.renderChapter(1L, "revision", 6, chapter);
        assertTrue(result.contains("```diagram-design"));
        assertFalse(result.contains("```mermaid"));
        var payload = JSONUtil.parseObj(result.substring(result.indexOf('{'), result.lastIndexOf('}') + 1));
        assertEquals("图4.1 系统架构", payload.getStr("caption"));
        assertEquals(svg.strip(), payload.getStr("svg").strip());
        assertTrue(payload.getStr("source").contains("页面"));
        assertTrue(result.endsWith("### 实现\n正文继续"));
        assertEquals(1, result.split("图4.1 系统架构", -1).length - 1);
        verify(tools).run(eq("diagram_design.py"), any(), eq(60), eq("validate"), anyString());
    }

    @Test void retriesLayoutOnceAndPreservesTheSourceWhenLayoutStillFails() {
        ThesisTools tools = mock(ThesisTools.class);
        ThesisWriter writer = mock(ThesisWriter.class);
        when(tools.root()).thenReturn(temporary);
        when(tools.run(eq("diagram_design.py"), any(), anyInt(), eq("extract"), anyString()))
                .thenReturn(JSONUtil.toJsonStr(Map.of("type", "flowchart", "mermaidSource", "flowchart TD\nA-->B",
                        "embeddedCaption", "图4.1 系统流程")));
        when(tools.run(eq("diagram_design.py"), any(), anyInt(), eq("validate"), anyString()))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "Missing source edge e1"));
        when(writer.drawDiagram(anyString(), anyString(), anyString(), anyString())).thenReturn("<svg/>");
        ThesisDiagramService service = new ThesisDiagramService();
        ReflectionTestUtils.setField(service, "tools", tools);
        ReflectionTestUtils.setField(service, "writer", writer);
        String result = service.renderChapter(1L, "r1", 6, "```mermaid\nflowchart TD\nA-->B\n\n图4.1 系统流程\n```");
        assertTrue(result.contains("```mermaid\nflowchart TD\nA-->B\n```\n\n图4.1 系统流程"));
        assertEquals(1, result.split("图4.1 系统流程", -1).length - 1);
        assertFalse(result.contains("```diagram-design"));
        assertFalse(result.contains("<svg/>"));
        verify(writer).drawDiagram(anyString(), anyString(), anyString(), eq(""));
        verify(writer).drawDiagram(anyString(), anyString(), anyString(), contains("Missing source edge e1"));
        verify(writer, times(2)).drawDiagram(anyString(), anyString(), anyString(), anyString());
    }

    @Test void continuesDrawingTheNextFigureAfterKeepingAnUnrenderableSketch() {
        ThesisTools tools = mock(ThesisTools.class);
        ThesisWriter writer = mock(ThesisWriter.class);
        when(tools.root()).thenReturn(temporary);
        when(tools.run(eq("diagram_design.py"), any(), anyInt(), eq("extract"), anyString())).thenReturn("{\"type\":\"flowchart\"}");
        when(tools.run(eq("diagram_design.py"), any(), anyInt(), eq("validate"), anyString()))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "Clipped text"))
                .thenThrow(new BusinessException(ErrorCode.OPERATION_ERROR, "Clipped text")).thenReturn("{}");
        when(writer.drawDiagram(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("<svg>failed</svg>", "<svg>failed</svg>", "<svg>validated</svg>");
        ThesisDiagramService service = new ThesisDiagramService();
        ReflectionTestUtils.setField(service, "tools", tools);
        ReflectionTestUtils.setField(service, "writer", writer);
        String result = service.renderChapter(1L, "r1", 6,
                "```mermaid\nflowchart TD\nA-->B\n```\n\n图4.1 流程\n\n```mermaid\nflowchart TD\nC-->D\n```\n\n图4.2 架构");
        assertEquals(1, result.split("```mermaid", -1).length - 1);
        assertEquals(1, result.split("```diagram-design", -1).length - 1);
        assertTrue(result.contains("validated"));
        assertFalse(result.contains("failed</svg>"));
        assertTrue(result.contains("图4.1 流程"));
        assertTrue(result.contains("图4.2 架构"));
    }

    @Test void plainChaptersDoNotStartPythonOrCallTheModel() {
        ThesisTools tools = mock(ThesisTools.class);
        ThesisWriter writer = mock(ThesisWriter.class);
        ThesisDiagramService service = new ThesisDiagramService();
        ReflectionTestUtils.setField(service, "tools", tools);
        ReflectionTestUtils.setField(service, "writer", writer);
        assertEquals("摘要正文", service.renderChapter(1L, "r1", 1, "摘要正文"));
        service.validateChapter(1L, "作者修订的摘要");
        verifyNoInteractions(tools, writer);
    }

    @Test void allRuntimeScriptsArePackagedAndCanBeExtractedWithoutTheSourceTree() throws Exception {
        ThesisTools tools = spy(new ThesisTools());
        doReturn(temporary).when(tools).root();
        Path root = tools.prepare();
        for (String name : ThesisTools.RESOURCES) assertTrue(Files.size(root.resolve(name)) > 0, name);
        assertSame(root, tools.prepare());
    }
}
