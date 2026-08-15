package com.aibuilder.lovableclone.common.security;

// Preview token se nikla hua permission. ownerId bhi saath aata hai taaki reads
// usi guarded path se guzrein jo /files use karta hai
public record PreviewGrant(Long projectId, Long ownerId) {}
