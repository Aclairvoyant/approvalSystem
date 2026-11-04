package com.approval.system.controller;

import com.approval.system.common.exception.BusinessException;
import com.approval.system.dto.MahjongActionRequest;
import com.approval.system.dto.MahjongCreateRequest;
import com.approval.system.dto.MahjongRoomResponse;
import com.approval.system.service.IMahjongService;
import com.approval.system.service.mahjong.MahjongActionType;
import com.approval.system.support.MockMvcSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static com.approval.system.support.MockMvcSupport.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MahjongControllerTest {

    @Mock
    private IMahjongService mahjongService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcSupport.standalone(new MahjongController(mahjongService));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPassesCurrentUserAndRequest() throws Exception {
        loginAs(10L);
        when(mahjongService.createRoom(eq(10L), any())).thenReturn(MahjongRoomResponse.builder().id(5L).build());

        mockMvc.perform(post("/api/mahjong/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"QIAOMA\",\"seatCount\":3,\"capPoints\":64}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(5));

        ArgumentCaptor<MahjongCreateRequest> request = ArgumentCaptor.forClass(MahjongCreateRequest.class);
        verify(mahjongService).createRoom(eq(10L), request.capture());
        assertThat(request.getValue().getSeatCount()).isEqualTo(3);
        assertThat(request.getValue().getCapPoints()).isEqualTo(64);
    }

    @Test
    void createRejectsInvalidSeatCountAndMode() throws Exception {
        loginAs(10L);

        mockMvc.perform(post("/api/mahjong/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BAIDA\",\"seatCount\":5}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("座位数只能是 2 到 4"));
        mockMvc.perform(post("/api/mahjong/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatCount\":4}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择玩法"));
        mockMvc.perform(post("/api/mahjong/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SICHUAN\",\"seatCount\":4}"))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(post("/api/mahjong/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BAIDA\",\"seatCount\":4,\"capPoints\":-1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("封顶分数不能小于 0"));

        verifyNoInteractions(mahjongService);
    }

    @Test
    void joinRequiresRoomCode() throws Exception {
        loginAs(10L);

        mockMvc.perform(post("/api/mahjong/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameCode\":\" \"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("房间号不能为空"));
        verifyNoInteractions(mahjongService);
    }

    @Test
    void seatAndActionRequestsAreValidated() throws Exception {
        loginAs(10L);

        mockMvc.perform(post("/api/mahjong/5/seat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatIndex\":0,\"bot\":true}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("座位不存在"));
        mockMvc.perform(post("/api/mahjong/5/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tile\":11}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请选择操作"));
        mockMvc.perform(post("/api/mahjong/5/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DISCARD\",\"tile\":99}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("牌不存在"));
        verifyNoInteractions(mahjongService);
    }

    @Test
    void outsiderGetsForbidden() throws Exception {
        loginAs(99L);
        when(mahjongService.getRoom(5L, 99L)).thenThrow(BusinessException.forbidden("你不在这个麻将房间里"));
        when(mahjongService.act(eq(5L), eq(99L), any(MahjongActionRequest.class)))
                .thenThrow(BusinessException.forbidden("你不是这桌的玩家"));

        mockMvc.perform(get("/api/mahjong/5"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("你不在这个麻将房间里"));
        mockMvc.perform(post("/api/mahjong/5/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PASS\"}"))
                .andExpect(jsonPath("$.code").value(403));

        ArgumentCaptor<MahjongActionRequest> action = ArgumentCaptor.forClass(MahjongActionRequest.class);
        verify(mahjongService).act(eq(5L), eq(99L), action.capture());
        assertThat(action.getValue().getType()).isEqualTo(MahjongActionType.PASS);
    }
}
