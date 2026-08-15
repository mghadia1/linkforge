package com.mghadia.linkforge.link.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLinkRequest(@NotBlank(message = "url must not be blank") String url) {
}
