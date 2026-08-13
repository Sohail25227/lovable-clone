package com.aibuilder.lovableclone.workspace.entity;

public enum ProjectStatusEnum {
    DRAFT,        // bana hai, AI nahi chala
    GENERATING,   // AI code likh raha hai
    READY,        // preview chal sakta hai
    FAILED        // generation fail
}
