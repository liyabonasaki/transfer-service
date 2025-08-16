package com.sun.transfer_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransferResponse {
    private String transferId;
    private String status;   // SUCCESS | FAILURE
    private String message;
}

