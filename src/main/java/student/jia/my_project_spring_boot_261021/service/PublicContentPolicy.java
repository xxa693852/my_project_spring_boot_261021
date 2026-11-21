package student.jia.my_project_spring_boot_261021.service;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
/** 僅檢查公開內容，不回傳命中詞或位置，不改寫原文。 */
@Service
public class PublicContentPolicy {
 public static final String REJECTION = "內容不符合發布規範，請調整後再試。";
 // 僅補已確認的倒序辱罵，不對一般句子反轉或任意重排字元。
 public static final String DEFAULT_TERMS = "幹你娘,干你娘,操你媽,操你妈,你媽死了,你妈死了,去你媽的,去你妈的,娘你幹,娘你干";
 private final String[] terms;
 private final AiContentReview ai;
 @org.springframework.beans.factory.annotation.Autowired
 public PublicContentPolicy(@Value("${app.moderation.blocked-terms:" + DEFAULT_TERMS + "}") String terms, AiContentReview ai) { this.terms=terms.split(","); this.ai=ai; }
 // 詞庫單元測試可獨立建立，不呼叫外部服務。
 public PublicContentPolicy(String terms) { this.terms=terms.split(","); this.ai=null; }
 public void validate(String... values) {
  for (String value : values) {
   if (value == null) continue;
   String normalized = normalize(value);
   for (String term : terms) {
    String blocked = normalize(term);
    if (!blocked.isEmpty() && normalized.contains(blocked)) throw new IllegalArgumentException(REJECTION);
   }
  }
  if (ai != null) {
   StringBuilder text = new StringBuilder();
   for (String value : values) if (value != null) text.append(value).append("\n");
   ai.validate(text.toString());
  }
 }
 private String normalize(String value) {
  // 只在比對副本將「妳」視為「你」，涵蓋既有詞庫的女性稱呼；不修改使用者原文。
  return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('妳', '你').replaceAll("[\\p{Z}\\p{P}\\p{Cf}]", "");
 }
}
