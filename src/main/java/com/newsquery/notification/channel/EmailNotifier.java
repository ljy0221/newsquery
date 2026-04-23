package com.newsquery.notification.channel;

import com.newsquery.notification.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Phase 6: Email 알림 채널
 * SMTP를 통해 이메일로 알림 전송
 * 설정: spring.mail.* 속성 필요
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class EmailNotifier implements Notifier {

    private final JavaMailSender mailSender;
    private static final String FROM_EMAIL = "noreply@newsquery.local";

    public EmailNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(Notification notification) {
        try {
            String userEmail = getUserEmail(notification.getUserId());
            if (userEmail == null) {
                return;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(FROM_EMAIL);
            message.setTo(userEmail);
            message.setSubject("[" + notification.getType() + "] N-QL 알림");
            message.setText(buildEmailBody(notification));

            mailSender.send(message);
        } catch (Exception e) {
            // 이메일 발송 실패가 다른 채널에 영향 X
            e.printStackTrace();
        }
    }

    private String getUserEmail(String userId) {
        // Phase 6.1: UserRepository에서 사용자 이메일 조회
        // 현재는 프로토타입이므로 하드코딩된 이메일 반환
        if ("anonymous".equals(userId)) {
            return System.getenv("USER_EMAIL");
        }
        return null;
    }

    private String buildEmailBody(Notification notification) {
        return String.format("""
            안녕하세요.

            %s

            알림 종류: %s
            발생 시간: %s

            N-QL Intelligence 팀
            """,
            notification.getMessage(),
            notification.getType(),
            notification.getCreatedAt()
        );
    }
}
