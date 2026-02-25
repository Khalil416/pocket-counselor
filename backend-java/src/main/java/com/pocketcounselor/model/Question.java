package com.pocketcounselor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Question(
    int id,
    int tier,
    String text,
    @JsonProperty("backup_question_id") Integer backupQuestionId
) {}
