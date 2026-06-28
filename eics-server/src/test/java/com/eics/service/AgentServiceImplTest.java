package com.eics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eics.entity.CsChatMessage;
import com.eics.entity.CsChatSession;
import com.eics.mapper.CsChatMessageMapper;
import com.eics.mapper.CsChatSessionMapper;
import com.eics.dialog.DialogService;
import com.eics.security.SensitiveWordFilter;
import com.eics.service.impl.AgentServiceImpl;
import com.eics.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentServiceImpl 单元测试 — 全 Mock 依赖
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentServiceImplTest {

    @Mock private CsChatSessionMapper sessionMapper;
    @Mock private CsChatMessageMapper messageMapper;
    @Mock private WebSocketSessionManager wsManager;
    @Mock private DialogService dialogService;
    @Mock private SensitiveWordFilter sensitiveWordFilter;

    @InjectMocks
    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        when(sensitiveWordFilter.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void testTransferNewSession() {
        when(sessionMapper.selectById("new-session")).thenReturn(null);

        Map<String, Object> result = agentService.transferToHuman("new-session");

        assertEquals("new-session", result.get("session_id"));
        assertEquals("WAITING", result.get("status"));
        verify(sessionMapper).insert(any(CsChatSession.class));
    }

    @Test
    void testTransferExistingSession() {
        CsChatSession existing = new CsChatSession();
        existing.setId("existing");
        existing.setStatus("BOT");
        when(sessionMapper.selectById("existing")).thenReturn(existing);

        Map<String, Object> result = agentService.transferToHuman("existing");

        assertEquals("WAITING", result.get("status"));
        verify(sessionMapper).updateById(existing);
    }

    @Test
    void testAcceptSession() {
        CsChatSession session = new CsChatSession();
        session.setId("s1");
        session.setStatus("WAITING");
        when(sessionMapper.selectById("s1")).thenReturn(session);
        when(messageMapper.selectBySessionId("s1")).thenReturn(new ArrayList<>());

        Map<String, Object> result = agentService.acceptSession("s1", 42L);

        assertEquals("AGENT", result.get("status"));
        assertEquals(42L, result.get("agent_id"));
        assertEquals("AGENT", session.getStatus());
    }

    @Test
    void testAcceptNonexistentThrows() {
        when(sessionMapper.selectById("bad")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> agentService.acceptSession("bad", 1L));
    }

    @Test
    void testSendMessage() {
        agentService.sendMessage("s1", 1L, "hello");
        verify(messageMapper).insert(any(CsChatMessage.class));
        verify(wsManager).broadcast(eq("s1"), isNull(), anyMap());
    }

    @Test
    void testSaveUserMessage() {
        agentService.saveUserMessage("s1", "help");
        verify(messageMapper).insert(any(CsChatMessage.class));
        verify(wsManager).broadcast(eq("s1"), isNull(), anyMap());
    }

    @Test
    void testCloseSession() {
        CsChatSession session = new CsChatSession();
        session.setId("s1");
        session.setStatus("AGENT");
        session.setRasaSenderId("user-abc");
        when(sessionMapper.selectById("s1")).thenReturn(session);

        agentService.closeSession("s1", 1L);

        assertEquals("CLOSED", session.getStatus());
        assertNotNull(session.getCloseTime());
        verify(sessionMapper).updateById(session);
        verify(wsManager).broadcast(eq("s1"), isNull(), anyMap());
        verify(dialogService).resetContext("s1");
    }

    @Test
    void testGetSessionMessages() {
        List<CsChatMessage> messages = new ArrayList<>();
        CsChatMessage msg1 = new CsChatMessage();
        msg1.setId(1L);  // DB auto-generated ID
        msg1.setSenderType("USER");
        msg1.setContent("hello");
        messages.add(msg1);

        when(messageMapper.selectBySessionId("s1")).thenReturn(messages);

        List<Map<String, Object>> result = agentService.getSessionMessages("s1");
        assertEquals(1, result.size());
        assertEquals("USER", result.get(0).get("sender_type"));
        assertEquals("hello", result.get(0).get("content"));
    }

    @Test
    void testGetWaitingSessions() {
        List<CsChatSession> sessions = new ArrayList<>();
        CsChatSession s1 = new CsChatSession();
        s1.setId("a");
        s1.setStatus("WAITING");
        sessions.add(s1);

        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sessions);

        List<Map<String, Object>> result = agentService.getWaitingSessions();
        assertEquals(1, result.size());
        assertEquals("a", result.get(0).get("session_id"));
    }

    @Test
    void testSensitiveWordFilterCalled() {
        // Verify filter is applied to outgoing messages
        agentService.sendMessage("s1", 1L, "badword");
        verify(sensitiveWordFilter).sanitize("badword");

        agentService.saveUserMessage("s1", "badword too");
        verify(sensitiveWordFilter).sanitize("badword too");
    }
}
