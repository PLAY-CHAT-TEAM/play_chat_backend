package me.kycho.playchat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import me.kycho.playchat.common.FileStore;
import me.kycho.playchat.domain.Member;
import me.kycho.playchat.repository.MemberRepository;
import me.kycho.playchat.security.jwt.JwtTokenProvider;
import me.kycho.playchat.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.restdocs.operation.OperationRequest;
import org.springframework.restdocs.operation.OperationRequestFactory;
import org.springframework.restdocs.operation.OperationRequestPart;
import org.springframework.restdocs.operation.OperationRequestPartFactory;
import org.springframework.restdocs.operation.preprocess.ContentModifyingOperationPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationPreprocessorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@AutoConfigureRestDocs
class MemberControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberService memberService;

    @Autowired
    JwtTokenProvider tokenProvider;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    FileStore fileStore;

    @Value("${backend.url}")
    String backendUrl;

    @Value("${file.dir}")
    String uploadFileDir;

    @Test
    @DisplayName("회원가입 테스트 정상")
    void signUpTest() throws Exception {

        // given
        String email = "member@email.com";
        String nickname = "member";
        String password = "aaaaaa1!";
        MockMultipartFile profileImage = new MockMultipartFile(
            "profileImage", "imageForTest.png", MediaType.IMAGE_PNG_VALUE,
            new FileInputStream("./src/test/resources/static/imageForTest.png")
        );

        given(fileStore.storeFile(profileImage)).willReturn("storeFileName");

        // when & then
        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .file(profileImage)
                    .param("email", email)
                    .param("nickname", nickname)
                    .param("password", password)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("email").value(email))
            .andExpect(jsonPath("nickname").value(nickname))
            .andDo(
                document("member-signup",
                    preprocessRequest(
                        new PartContentModifyingPreprocessor()
                    ),
                    preprocessResponse(
                        prettyPrint()
                    ),
                    requestHeaders(
                        headerWithName(HttpHeaders.CONTENT_TYPE)
                            .description("요청 메시지의 콘텐츠 타입 +" + "\n" + MediaType.MULTIPART_FORM_DATA),
                        headerWithName(HttpHeaders.ACCEPT)
                            .description("응답받을 콘텐츠 타입 +" + "\n" + MediaType.APPLICATION_JSON)
                    ),
                    requestParameters(
                        parameterWithName("email").description("회원가입에 사용할 이메일 (필수)"),
                        parameterWithName("password").description("회원가입에 사용할 비빌번호 (필수)"),
                        parameterWithName("nickname").description("회원가입에 사용할 닉네임 (필수)")
                    ),
                    requestParts(
                        partWithName("profileImage")
                            .description("프로필 사진으로 사용될 이미지 파일 +" + "\n" + "(없으면 기본이미지 사용)")
                    ),
                    responseFields(
                        fieldWithPath("email").description("회원가입이 왼료된 회원의 이메일"),
                        fieldWithPath("nickname").description("회원가입이 왼료된 회원의 닉네임")
                    )
                )
            );
    }

    @Test
    @DisplayName("회원가입 테스트 정상 (프로필이미지 없이)")
    void signUpTest_without_profileImage() throws Exception {

        // given
        String email = "member@email.com";
        String nickname = "member";
        String password = "aaaaaa1!";

        // when & then
        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .param("email", email)
                    .param("nickname", nickname)
                    .param("password", password)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("email").value(email))
            .andExpect(jsonPath("nickname").value(nickname));

        Member signedUpMember = memberRepository.findByEmail(email).get();
        assertThat(signedUpMember.getImageUrl()).isEqualTo(
            backendUrl + "/images/default-profile.png");
    }

    @Test
    @DisplayName("회원가입 테스트 ERROR(이메일 중복)")
    void signUpErrorTest_duplicatedEmail() throws Exception {

        // given
        String duplicatedEmail = "member@email.com";

        memberRepository.save(Member.builder()
            .email(duplicatedEmail)
            .nickname("member")
            .password("password")
            .imageUrl("image_url")
            .build());

        // when & then
        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .param("email", duplicatedEmail)
                    .param("nickname", "nickname")
                    .param("password", "aaaaaa1!")
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("status").value(HttpStatus.CONFLICT.value()))
            .andExpect(jsonPath("message").value("Duplicated Email."))
        ;
    }

    @DisplayName("회원가입 테스트 ERROR(잘못된 이메일)")
    @ParameterizedTest(name = "{index}: 잘못된 이메일 : {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"aaa", "aaa@", "@bbb"})
    void signUpErrorTest_wrongEmail(String wrongEmail) throws Exception {

        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .param("email", wrongEmail)
                    .param("nickname", "nickname")
                    .param("password", "aaaaaa1!")
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("status").value(400))
            .andExpect(jsonPath("message").value("Binding Error."))
            .andExpect(jsonPath("fieldErrors[0].field").value("email"))
            .andExpect(jsonPath("fieldErrors[0].defaultMessage").exists())
            .andExpect(jsonPath("fieldErrors[0].rejectedValue").value(wrongEmail))
//            TODO : docs
//            .andDo(
//                document("member-signup-error")
//            )
        ;
    }

    @DisplayName("회원가입 테스트 ERROR(잘못된 비밀번호)")
    @ParameterizedTest(name = "{index}: 잘못된 비밀번호 : {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"aaaa!@2", "ccc@3cccccccccccc", "bbbbbbbb1", "AAAAAAAAA!", "@11111111"})
    void signUpErrorTest_wrongPassword(String wrongPassword) throws Exception {

        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .param("email", "member@email.com")
                    .param("nickname", "nickname")
                    .param("password", wrongPassword)
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("status").value(400))
            .andExpect(jsonPath("message").value("Binding Error."))
            .andExpect(jsonPath("fieldErrors[0].field").value("password"))
            .andExpect(jsonPath("fieldErrors[0].defaultMessage").exists())
            .andExpect(jsonPath("fieldErrors[0].rejectedValue").value(wrongPassword))
        ;
    }

    @DisplayName("회원가입 테스트 ERROR(잘못된 닉네임)")
    @ParameterizedTest(name = "{index}: 잘못된 닉네임 : {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"aaaaaaaaaabbbbbbbbbbccccccccccddddddddddeeeeeeeeeef"})
    void signUpErrorTest_wrongNickname(String wrongNickname) throws Exception {

        mockMvc.perform(
                multipart("/api/members/sign-up")
                    .param("email", "member@email.com")
                    .param("nickname", wrongNickname)
                    .param("password", "aaaaaaaa1!")
                    .contentType(MediaType.MULTIPART_FORM_DATA_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("status").value(400))
            .andExpect(jsonPath("message").value("Binding Error."))
            .andExpect(jsonPath("fieldErrors[0].field").value("nickname"))
            .andExpect(jsonPath("fieldErrors[0].defaultMessage").exists())
            .andExpect(jsonPath("fieldErrors[0].rejectedValue").value(wrongNickname))
        ;
    }

    @Test
    @DisplayName("id로 회원 조회 정상")
    void getMemberTest() throws Exception {
        // given
        Long targetId = 3L;
        createMembers(10);

        // when & then
        mockMvc.perform(
            get("/api/members/" + targetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken())
        )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("id").value(targetId))
            .andExpect(jsonPath("email").value("member" + targetId + "@email.com"))
            .andExpect(jsonPath("nickname").value("member" + targetId))
            .andExpect(jsonPath("imageUrl").exists())
            .andExpect(jsonPath("password").doesNotExist())
        ;
    }

    @Test
    @DisplayName("프로필 이미지 조회 정상")
    void downloadImageTest() throws Exception {

        // given
        String filename = "profileImage.png";
        File file = new File("./src/test/resources/static/imageForTest.png");
        File uploadedFile = new File(uploadFileDir + filename);
        Files.copy(file.toPath(), uploadedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        given(fileStore.getFullPath(filename)).willReturn(uploadFileDir + filename);

        // when & then
        mockMvc.perform(
                get("/api/members/profile-image/" + filename)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken())
            )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
            .andExpect(content().bytes(Files.readAllBytes(file.toPath())))
            .andDo(
                document("member-profileimage",
                    preprocessRequest(
                        new AuthHeaderModifyingPreprocessor()
                    ),
                    preprocessResponse(
                        new ContentModifyingOperationPreprocessor((originalContent, contentType) ->
                            "<< Image binary data >>".getBytes(StandardCharsets.UTF_8))
                    ),
                    requestHeaders(
                        headerWithName(HttpHeaders.AUTHORIZATION)
                            .description("인증 정보 헤더 +" + "\n" + "Bearer <jwt토큰값>")
                    )
                )
            );

        uploadedFile.delete();
    }

    @Test
    @DisplayName("프로필 이미지 조회 ERROR(존재하지 않는 이미지)")
    void downloadImageTest_notFound() throws Exception {

        // when & then
        mockMvc.perform(
                get("/api/members/profile-image/noFile.png")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + generateToken())
            )
            .andExpect(status().isNotFound());
    }

    private void createMembers(int memberNum) {
        for (int i = 1; i <= memberNum; i++) {
            Member member = Member.builder()
                .email("member" + i + "@email.com")
                .password("aaaaaaa1!")
                .nickname("member" + i)
                .imageUrl("image_url")
                .build();
            memberService.signUp(member);
        }
    }

    private String generateToken() {

        String email = "test@naver.com";
        List<GrantedAuthority> authorities = Collections
            .singletonList(new SimpleGrantedAuthority("ROLE_MEMBER"));

        Authentication authentication =
            new UsernamePasswordAuthenticationToken(email, "", authorities);

        return tokenProvider.createToken(authentication);
    }

    static final class PartContentModifyingPreprocessor extends OperationPreprocessorAdapter {

        private final OperationRequestPartFactory partFactory = new OperationRequestPartFactory();
        private final OperationRequestFactory requestFactory = new OperationRequestFactory();

        @Override
        public OperationRequest preprocess(OperationRequest request) {
            List<OperationRequestPart> parts = new ArrayList<>();
            for (OperationRequestPart part : request.getParts()) {
                parts.add(partFactory.create(part.getName(), part.getSubmittedFileName(),
                    "<< binary data >>".getBytes(), part.getHeaders()));
            }
            return requestFactory.create(request.getUri(), request.getMethod(),
                request.getContent(), request.getHeaders(), request.getParameters(), parts);
        }
    }

    static final class AuthHeaderModifyingPreprocessor extends OperationPreprocessorAdapter {

        private final OperationRequestFactory requestFactory = new OperationRequestFactory();

        @Override
        public OperationRequest preprocess(OperationRequest request) {
            HttpHeaders headers = new HttpHeaders();
            for (String key : request.getHeaders().keySet()) {
                if (key.equals(HttpHeaders.AUTHORIZATION)) {
                    headers.put(key, Collections.singletonList("Bearer XXXXXXX.YYYYYYYYYY.ZZZZZZ"));
                } else {
                    headers.put(key, Objects.requireNonNull(request.getHeaders().get(key)));
                }
            }

            return requestFactory.create(request.getUri(), request.getMethod(),
                request.getContent(), headers, request.getParameters(), request.getParts());
        }
    }
}
