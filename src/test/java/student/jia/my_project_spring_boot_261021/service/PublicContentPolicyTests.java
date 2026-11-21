package student.jia.my_project_spring_boot_261021.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PublicContentPolicyTests {
 private final PublicContentPolicy policy=new PublicContentPolicy("幹你娘,操你媽");
 @Test void normalDnDVocabularyIsAllowed() {
  assertDoesNotThrow(() -> policy.validate("屠龍、邪教、殺死敵人、死亡豁免、刺客、血魔法、幹掉哥布林，幹練的角色"));
 }
 @Test void normalizedAbuseAlwaysReturnsSameGenericMessage() {
  for(String text:new String[]{"前文幹你娘後文","操 你 媽","幹\u200B你娘"}) {
   assertEquals(PublicContentPolicy.REJECTION,assertThrows(IllegalArgumentException.class,()->policy.validate(text)).getMessage());
  }
 }
 @Test void confirmedReversedVariantsAreRejectedByActualDefaultDictionary() {
  PublicContentPolicy defaults = new PublicContentPolicy(PublicContentPolicy.DEFAULT_TERMS);
  for (String text : new String[] {"娘你幹", "娘你干", "娘 你 幹", "娘，你，幹", "娘\u200B你幹", "娘 你 干"}) {
   assertEquals(PublicContentPolicy.REJECTION, assertThrows(IllegalArgumentException.class, () -> defaults.validate(text)).getMessage());
  }
 }
 @Test void femininePronounVariantsUseTheSameDictionaryRules() {
  PublicContentPolicy defaults = new PublicContentPolicy(PublicContentPolicy.DEFAULT_TERMS);
  for (String text : new String[] {"娘妳幹", "娘妳干", "娘 妳 幹", "娘，妳，幹", "娘\u200B妳幹", "幹妳娘", "操妳媽", "妳媽死了", "去妳媽的"}) {
   assertEquals(PublicContentPolicy.REJECTION, assertThrows(IllegalArgumentException.class, () -> defaults.validate(text)).getMessage());
  }
  assertDoesNotThrow(() -> defaults.validate("妳要一起屠龍、調查邪教嗎？", "娘親稱讚妳很幹練。"));
 }
 @Test void normalSentencesAreNotReversedOrArbitrarilyReassembled() {
  PublicContentPolicy defaults = new PublicContentPolicy(PublicContentPolicy.DEFAULT_TERMS);
  assertDoesNotThrow(() -> defaults.validate("娘親問你是否幹練，屠龍後回家。", "殺死惡魔並調查邪教；幹掉哥布林。"));
 }
 @Test void reversedAbuseIsBlockedBeforeAnyAiRequest() throws Exception {
  AiModerationGateway gateway = org.mockito.Mockito.mock(AiModerationGateway.class);
  AiContentReview ai = new AiContentReview(gateway,true,"test-key",java.time.Clock.systemUTC());
  PublicContentPolicy defaults = new PublicContentPolicy(PublicContentPolicy.DEFAULT_TERMS,ai);
  assertThrows(IllegalArgumentException.class, () -> defaults.validate("娘 妳 幹"));
  org.mockito.Mockito.verifyNoInteractions(gateway);
 }

}
