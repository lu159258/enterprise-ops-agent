package com.luzihao.api.dto;

public record IngestResult(String documentId, String title, int version, String checksum) {
}
