package com.robustvision.platform.service;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class JobProgressTest {
    @Test void boundsWritesAndChecksCancellationEvenWhenPageProgressDoesNotChange() {
        var now=new AtomicLong();var writes=new ArrayList<Integer>();
        var progress=new JobProgress(Instant.now().plusSeconds(60),now::get,v->{writes.add(v);return writes.size()<3?1:0;});
        progress.accept(5);
        for(int i=1;i<4;i++){now.set(i*250_000_000L);progress.accept(10);}
        assertThat(writes).containsExactly(5);
        now.set(1_000_000_000L);progress.accept(5);
        assertThat(writes).containsExactly(5,10);
        now.set(2_000_000_000L);
        assertThatThrownBy(()->progress.accept(10)).isInstanceOf(IllegalStateException.class);
    }
    @Test void expiredLeaseCannotWriteProgress() {
        var progress=new JobProgress(Instant.now().minusSeconds(1),()->0L,v->{throw new AssertionError("Expired worker wrote");});
        assertThatThrownBy(()->progress.accept(10)).isInstanceOf(IllegalStateException.class);
    }
}
