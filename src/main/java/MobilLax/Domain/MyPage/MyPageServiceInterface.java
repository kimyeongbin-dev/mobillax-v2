package MobilLax.Domain.MyPage;

public interface MyPageServiceInterface {
    MyPageDto getMyPageInfo(String email);
    void updateProfile(String email, String name, String password);
    void deleteAccountByEmail(String email);
}
