package com.pocketcounselor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pocketcounselor.model.Session;
import com.pocketcounselor.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired SessionService service;

    private JsonNode start() throws Exception {
        var res = mvc.perform(post("/api/session/start")).andExpect(status().isOk()).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString());
    }

    private void awaitScoring(Session s) throws InterruptedException {
        for (int i=0;i<50;i++) {
            boolean pending = s.answers.stream().anyMatch(a -> "pending_scoring".equals(a.status));
            if (!pending) return;
            Thread.sleep(20);
        }
        fail("scoring timeout");
    }

    @Test
    void sessionInitHasAllScoresIncludingInvalid() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        Session s = service.get(id);
        assertEquals(0, s.microSkillScores.get("INVALID"));
        assertEquals(78, s.microSkillScores.size());
    }

    @Test
    void skipWorksUpToSevenAndEighthRejected() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        int q = st.get("question").get("id").asInt();
        for (int i=0;i<7;i++) {
            var r = mvc.perform(post("/api/session/"+id+"/skip").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionId\":"+q+"}"));
            r.andExpect(status().isOk());
            q = mapper.readTree(r.andReturn().getResponse().getContentAsString()).path("nextQuestion").path("id").asInt();
        }
        mvc.perform(post("/api/session/"+id+"/skip").contentType(MediaType.APPLICATION_JSON).content("{\"questionId\":"+q+"}"))
                .andExpect(status().isBadRequest());
        Session s = service.get(id);
        assertEquals(7, s.counters.questionsSkipped);
        assertEquals(0, s.counters.questionsAnswered);
        assertEquals(0, s.scoringAttempts.get());
    }

    @Test
    void shortAnswerRejectedNoAdvanceNoAiCall() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        int q = st.get("question").get("id").asInt();
        mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":"+q+",\"answerText\":\"short\"}"))
                .andExpect(status().isBadRequest());
        Session s = service.get(id);
        assertEquals(1, s.counters.questionsShown);
        assertEquals(0, s.scoringAttempts.get());
    }

    @Test
    void validScoringUpdatesTotalsAndAnswered() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        int q = st.get("question").get("id").asInt();
        mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":"+q+",\"answerText\":\"this is a sufficiently long valid answer\"}"))
                .andExpect(status().isOk());
        Session s = service.get(id);
        awaitScoring(s);
        assertEquals(1, s.counters.questionsAnswered);
        assertEquals(8, s.points.total);
        assertEquals(5, s.microSkillScores.get("empathy"));
    }

    @Test
    void invalidScoringIncrementsInvalidAndAnswered() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        int q = st.get("question").get("id").asInt();
        mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":"+q+",\"answerText\":\"this is long [force_invalid]\"}"))
                .andExpect(status().isOk());
        Session s = service.get(id); awaitScoring(s);
        assertEquals(1, s.counters.invalidAnswers);
        assertEquals(1, s.counters.questionsAnswered);
        assertEquals(1, s.microSkillScores.get("INVALID"));
        assertEquals(0, s.points.total);
    }

    @Test
    void checkpointOnlyAtAnsweredModuloThreeAndNoRepeat() throws Exception {
        JsonNode st = start();
        String id = st.get("sessionId").asText();
        int q = st.get("question").get("id").asInt();
        for (int i=0;i<11;i++) {
            var r = mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionId\":"+q+",\"answerText\":\"this is long [force_high]\"}"))
                    .andExpect(status().isOk()).andReturn();
            q = mapper.readTree(r.getResponse().getContentAsString()).path("nextQuestion").path("id").asInt();
        }
        Session s = service.get(id); awaitScoring(s);
        assertEquals(0, s.checkpoints.reached);

        mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":"+q+",\"answerText\":\"this is long [force_high]\"}"))
                .andExpect(status().isOk());
        awaitScoring(s);
        assertEquals(1, s.checkpoints.reached);
    }

    @Test
    void aiFailureRetriesThenMarksFailedNoPoints() throws Exception {
        JsonNode st = start(); String id = st.get("sessionId").asText(); int q = st.get("question").get("id").asInt();
        mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":"+q+",\"answerText\":\"this is long [force_ai_fail]\"}"))
                .andExpect(status().isOk());
        Session s = service.get(id); awaitScoring(s);
        assertTrue(s.answers.stream().anyMatch(a -> "ai_failed".equals(a.status)));
        assertEquals(2, s.scoringAttempts.get());
        assertEquals(0, s.points.total);
        assertEquals(0, s.counters.questionsAnswered);
    }

    @Test
    void limit35AllowsResultsWithoutCheckpoint() throws Exception {
        JsonNode st = start(); String id = st.get("sessionId").asText(); int q = st.get("question").get("id").asInt();
        for (int i=0;i<35;i++) {
            var r = mvc.perform(post("/api/session/"+id+"/answer").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"questionId\":"+q+",\"answerText\":\"this is a sufficiently long valid answer\"}"))
                    .andExpect(status().isOk()).andReturn();
            q = mapper.readTree(r.getResponse().getContentAsString()).path("nextQuestion").path("id").asInt();
        }
        Session s = service.get(id); awaitScoring(s);
        assertEquals(35, s.counters.questionsShown);
        assertEquals(0, s.checkpoints.reached);

        var result = mvc.perform(post("/api/session/"+id+"/results")).andExpect(status().isOk()).andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("low_data_quality_warning").asBoolean());
    }

}