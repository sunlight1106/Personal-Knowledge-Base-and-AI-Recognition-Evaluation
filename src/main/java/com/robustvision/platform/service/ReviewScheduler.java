package com.robustvision.platform.service;

import io.github.openspacedrepetition.*;
import org.springframework.stereotype.Service;
import java.time.*;

/** FSRS-6 state uses each item's real review history; default weights are not presented as fitted weights. */
@Service
public class ReviewScheduler {
    public record Scheduled(String state,Instant due) {}
    public Scheduled review(String saved,int grade,double retention,Instant now) {
        if(grade<0||grade>3||!Double.isFinite(retention)||retention<0.8||retention>0.97)throw new IllegalArgumentException("Invalid scheduling input");
        Scheduler scheduler=Scheduler.builder().desiredRetention(retention).learningSteps(new Duration[]{}).relearningSteps(new Duration[]{}).maximumInterval(365).enableFuzzing(false).build();
        Card card=saved==null||saved.isBlank()?Card.builder().build():parse(saved);
        Card result=scheduler.reviewCard(card,new Rating[]{Rating.AGAIN,Rating.HARD,Rating.GOOD,Rating.EASY}[grade],now).card();
        return new Scheduled(result.toJson(),result.getDue());
    }

    static Card parse(String json) {
        Card card=Card.fromJson(json);
        Double stability=card.getStability(),difficulty=card.getDifficulty();
        if(card.getState()==null||card.getDue()==null
                ||stability!=null&&(!Double.isFinite(stability)||stability<=0||stability>100000)
                ||difficulty!=null&&(!Double.isFinite(difficulty)||difficulty<1||difficulty>10)
                ||(stability==null)!=(difficulty==null)
                ||card.getStep()!=null&&(card.getStep()<0||card.getStep()>100)
                ||card.getDue().isAfter(Instant.parse("9999-12-31T00:00:00Z")))
            throw new IllegalArgumentException("Invalid scheduling state");
        return card;
    }
}
