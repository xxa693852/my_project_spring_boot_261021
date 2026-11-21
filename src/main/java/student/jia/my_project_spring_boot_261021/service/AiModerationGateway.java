package student.jia.my_project_spring_boot_261021.service;
/** 審核供應商的替換介面；服務故障必須與內容拒絕分開。 */
public interface AiModerationGateway {
    enum Decision { ALLOW, BLOCK }
    Decision review(String text) throws Exception;
}
