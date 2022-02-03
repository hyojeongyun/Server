package com.example.mummoomserver.login.users;

import com.example.mummoomserver.login.authentication.oauth2.userInfo.OAuthAttributes;
import com.example.mummoomserver.login.security.UserDetailsImpl;
import com.example.mummoomserver.login.validation.SimpleFieldError;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OAuth2AccountRepository oAuth2AccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override //일반 회원 가입 시 필요한 정보들
    public void saveUser(SignUpRequest signUpRequest){
        checkDuplicateEmail(signUpRequest.getEmail());
        User user = User.builder()
                .username(signUpRequest.getEmail())
                .nickName(signUpRequest.getNickName())
                .email(signUpRequest.getEmail())
                .imgUrl(signUpRequest.getImgUrl())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .type(UserType.DEFAULT)
                .build();
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true) // 정보들을 가져올 수 있으려나..,?모르겠다.
    public Optional<OAuth2AccountDTO> getOAuth2Account(String username) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (!optionalUser.isPresent() || optionalUser.get().getSocial() == null) return Optional.empty();
        return Optional.of(optionalUser.get().getSocial().toDTO());
    }

    @Override
    public void updateProfile(String username, UpdateProfileRequest updateProfileRequest){

        User user = userRepository.findByUsername(username).get();

        //이름이 변경되었는지 체크
        if (!user.getNickName().equals(updateProfileRequest.getNickName()))
            user.updateName(updateProfileRequest.getNickName());

        //이메일이 변경되었는지 체크
        if (!user.getEmail().equals(updateProfileRequest.getEmail())) {
            checkDuplicateEmail(updateProfileRequest.getEmail());
            user.updateEmail(updateProfileRequest.getEmail());
        }
    }


    @Override  //잘하면 사용할 수 있을 것 같아보임 oauth2userinfo부분을 atattribute로 대체할 수 있는 지 살펴보기
    // 카카오 구글 일반 유저에 대한 개발 oauthattributes 처리가 필요하다. 이거는 물어보기
    public UserDetails loginOAuth2User(String provider, OAuth2Token oAuth2Token, OAuthAttributes userInfo) {

        Optional<OAuth2Account> optOAuth2Account = oAuth2AccountRepository.findByProviderAndProviderId(provider, userInfo.getUserIdx());
        User user = null;

        //가입된 계정이 존재할때
        if (optOAuth2Account.isPresent()) {
            OAuth2Account oAuth2Account = optOAuth2Account.get();
            user = oAuth2Account.getUser();
            //토큰 업데이트
            oAuth2Account.updateToken(oAuth2Token.getToken(), oAuth2Token.getRefreshToken(), oAuth2Token.getExpiredAt());
        }
        //가입된 계정이 존재하지 않을때
        else {
            //소셜 계정 정보 생성
            OAuth2Account newAccount = OAuth2Account.builder()
                    .provider(provider)
                    .providerId(userInfo.getUserIdx())
                    .token(oAuth2Token.getToken())
                    .refreshToken(oAuth2Token.getRefreshToken())
                    .tokenExpiredAt(oAuth2Token.getExpiredAt()).build();
            oAuth2AccountRepository.save(newAccount);

            //이메일 정보가 있을때
            if (userInfo.getEmail() != null) {
                // 같은 이메일을 사용하는 계정이 존재하는지 확인 후 있다면 소셜 계정과 연결시키고 없다면 새로 생성한다
                user = userRepository.findByEmail(userInfo.getEmail())
                        .orElse(User.builder()
                                .username(provider + "_" + userInfo.getUserIdx())
                                .nickName(userInfo.getNickName())
                                .email(userInfo.getEmail())
                                .type(UserType.OAUTH)
                                .build());
            }
            //이메일 정보가 없을때
            else {
                user = User.builder()
                        .username(provider + "_" + userInfo.getUserIdx())
                        .nickName(userInfo.getNickName())
                        .type(UserType.OAUTH)
                        .build();
            }

            //새로 생성된 유저이면 db에 저장
            if (user.getUserIdx() == null)
                userRepository.save(user);

            //연관관계 설정
            user.linkSocial(newAccount);
        }

        return UserDetailsImpl.builder()
                .userIdx(user.getUserIdx())
                .username(user.getUsername())
                .nickName(user.getNickName())
                .email(user.getEmail())
                .type(user.getType())
                .authorities(user.getAuthorities()).build();
    }

    @Override
    // 위와 동일한 문제
    public UserDetails linkOAuth2Account(String username, String provider, OAuth2Token oAuth2Token, OAuth2UserInfo userInfo) {
        User user = checkRegisteredUser(username);

        //이미 등록된 소셜 계정이라면 연동된 계정이 존재
        Assert.state(oAuth2AccountRepository.existsByProviderAndProviderId(provider, userInfo.getUserIdx()) == false, "소셜 계정에 연동된 계정이 이미 존재합니다.");

        //소셜 계정 정보 생성
        OAuth2Account oAuth2Account = OAuth2Account.builder()
                .provider(provider)
                .providerId(userInfo.getUserIdx())
                .token(oAuth2Token.getToken())
                .refreshToken(oAuth2Token.getRefreshToken())
                .tokenExpiredAt(oAuth2Token.getExpiredAt())
                .build();
        oAuth2AccountRepository.save(oAuth2Account);

        //연관관계 설정
        user.linkSocial(oAuth2Account);

        return UserDetailsImpl.builder()
                .userIdx(user.getUserIdx())
                .username(user.getUsername())
                .nickName(user.getNickName())
                .email(user.getEmail())
                .type(user.getType())
                .authorities(user.getAuthorities()).build();
    }

    @Override // 할 수 있다.
    public OAuth2AccountDTO unlinkOAuth2Account(String username) {
        User user = checkRegisteredUser(username);

        //연관관계 해제  oauth2 계정을 user 세션이나 다른 것으로 변환 필요하다.
        OAuth2Account oAuth2Account = user.getSocial();
        OAuth2AccountDTO oAuth2AccountDTO = oAuth2Account.toDTO();
        user.unlinkSocial();
        oAuth2AccountRepository.delete(oAuth2Account);

        return oAuth2AccountDTO;
    }

    @Override //위와 동일한 이유
    public Optional<OAuth2AccountDTO> withdrawUser(String username) {
        OAuth2AccountDTO oAuth2AccountDTO = null;
        User user = checkRegisteredUser(username);
        //연동된 소셜 계정이 있다면 계정 정보를 리턴하기 위해 저장
        if(user.getSocial() != null)
            oAuth2AccountDTO = user.getSocial().toDTO();
        userRepository.delete(user);
        return Optional.ofNullable(oAuth2AccountDTO);
    }

    private void checkDuplicateEmail(String email) { //이메일 확인은 쌉가능
        if(userRepository.existsByEmail(email))
            throw new DuplicateUserException("사용중인 이메일입니다.", new SimpleFieldError("email", "사용중인 이메일 입니다."));
    }

    private User checkRegisteredUser(String username) { //등록 회원 확인 쌉가능
        Optional<User> optUser = userRepository.findByUsername(username);
        Assert.state(optUser.isPresent(), "가입되지 않은 회원입니다.");
        return optUser.get();
    }
}
