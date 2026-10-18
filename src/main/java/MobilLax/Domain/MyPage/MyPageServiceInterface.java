package MobilLax.Domain.MyPage;

public interface MyPageServiceInterface {
    MyPageDto getMyPageInfo(String email);
    void updateProfile(String email, String name);
    void changePassword(String email, String currentPassword, String newPassword);
    void deleteAccountByEmail(String email);
}
