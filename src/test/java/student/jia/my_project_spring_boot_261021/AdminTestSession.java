package student.jia.my_project_spring_boot_261021;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import student.jia.my_project_spring_boot_261021.entity.Member;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 整合測試也經由專用 API 登入，不直接塞入未驗證的管理員 ID。 */
class AdminTestSession implements RequestPostProcessor {
    private final MockHttpSession session;
    private final String csrf;

    AdminTestSession(MockMvc mvc, Member admin, String password) throws Exception {
        var result = mvc.perform(post("/admin/auth/login").header("X-Admin-Login", "1")
                .contentType("application/json").content("{\"account\":\"" + admin.getAccount()
                        + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        session = (MockHttpSession) result.getRequest().getSession(false);
        csrf = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.csrfToken");
    }

    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
        request.setSession(session);
        request.addHeader("X-Admin-CSRF", csrf);
        return request;
    }
}
