package MobilLax.Domain.Admin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Map;

/**
 * QR 코드를 서버에서 SVG 로 그린다 (v2.11)
 * - OTP 등록 주소에는 비밀값이 들어 있으므로 외부 QR 생성 서비스에 보내지 않는다
 * - 결과는 숫자 좌표와 고정 태그뿐이라 템플릿에 그대로 넣어도 된다 (입력 문자열이 SVG 에 들어가지 않음)
 */
final class QrSvg {

    private static final int MODULE = 4;
    private static final int QUIET_ZONE = 4;

    private QrSvg() {
    }

    static String render(String content) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, QUIET_ZONE));
        } catch (WriterException e) {
            throw new IllegalStateException("QR 코드를 만들지 못했습니다");
        }
        int size = matrix.getWidth() * MODULE;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x * MODULE).append(' ').append(y * MODULE)
                            .append("h").append(MODULE).append('v').append(MODULE).append("h-").append(MODULE).append('z');
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + size + "\" height=\"" + size
                + "\" viewBox=\"0 0 " + size + " " + size + "\" role=\"img\" aria-label=\"OTP 등록 QR 코드\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }
}
