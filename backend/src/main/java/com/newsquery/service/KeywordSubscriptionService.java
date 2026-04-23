package com.newsquery.service;

import com.newsquery.domain.KeywordSubscription;
import com.newsquery.repository.KeywordSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class KeywordSubscriptionService {

    private final KeywordSubscriptionRepository repository;

    public KeywordSubscriptionService(KeywordSubscriptionRepository repository) {
        this.repository = repository;
    }

    public KeywordSubscription subscribe(String userId, String keyword) {
        KeywordSubscription subscription = new KeywordSubscription(userId, keyword);
        return repository.save(subscription);
    }

    public void unsubscribe(String subscriptionId) {
        repository.deleteById(subscriptionId);
    }

    public void unsubscribeByKeyword(String userId, String keyword) {
        repository.findByUserIdAndKeyword(userId, keyword).ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public List<String> getSubscribedKeywords(String userId) {
        return repository.findByUserIdAndIsActive(userId, true).stream()
                .map(KeywordSubscription::getKeyword)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KeywordSubscription> getSubscriptions(String userId) {
        return repository.findByUserIdAndIsActive(userId, true);
    }

    @Transactional(readOnly = true)
    public List<KeywordSubscription> getAllSubscriptions(String userId) {
        return repository.findByUserId(userId);
    }

    public void toggleSubscription(String subscriptionId, boolean isActive) {
        repository.findById(subscriptionId).ifPresent(sub -> {
            sub.setActive(isActive);
            repository.save(sub);
        });
    }
}
