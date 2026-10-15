package com.shareloop.setting.service;

import com.shareloop.setting.dto.LexiconMatch;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LexiconService {

    private final ConfigService configService;
    private final LexiconMatcher matcher;

    public LexiconService(ConfigService configService, LexiconMatcher matcher) {
        this.configService = configService;
        this.matcher = matcher;
    }

    public List<LexiconMatch> match(String text) {
        return matcher.match(text, configService.lexicon());
    }
}
