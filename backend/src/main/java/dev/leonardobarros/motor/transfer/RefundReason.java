package dev.leonardobarros.motor.transfer;

/** Motivos de devolução, com os códigos ISO 20022 que vão na mensagem pacs.004. */
public enum RefundReason {

    CUSTOMER_REQUEST("MD06"),
    BANK_ERROR("BE08"),
    FRAUD("FR01");

    private final String isoCode;

    RefundReason(String isoCode) {
        this.isoCode = isoCode;
    }

    public String isoCode() {
        return isoCode;
    }
}
