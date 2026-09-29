package com.example.payout_batch_optimizer.optimizer;

import com.example.payout_batch_optimizer.exception.BatchTooLargeException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class Optimizer {

    private static final long MAX_CELLS = 50_000_000L;

    public List<PayoutDecision> optimizePayoutBatch(List<PayoutCandidate> candidates, BigDecimal availablePayoutFloat) {
        int candidatesSize = candidates.size();

        long[] amounts = new long[candidatesSize];
        long[] commissions = new long[candidatesSize];

        for(int i = 0; i < candidatesSize; i++) {
            amounts[i] = toTetri(candidates.get(i).payoutAmount());
            commissions[i] = toTetri(candidates.get(i).agentCommission());
        }

        long capacity = toTetri(availablePayoutFloat);
        if(capacity + 1 > MAX_CELLS / candidatesSize) {
            throw new BatchTooLargeException("Batch too large to optimize: "
                    + candidatesSize + " requests x " + (capacity + 1) + " float cents exceeds " + MAX_CELLS + " cells");
        }
        int capacityCents = (int) capacity;

        long best[] = new long[capacityCents + 1];
        boolean[][] taken = new boolean[candidatesSize][capacityCents + 1];

        for(int i = 0; i < candidatesSize; i++) {
            for(int c = capacityCents; c >= amounts[i]; c--) {
                if(best[c] < best[c - (int)amounts[i]] + commissions[i]) {
                    best[c] = best[c - (int)amounts[i]] + commissions[i];
                    taken[i][c] = true;
                }
            }
        }

        int c = 0;
        while(best[c] < best[capacityCents]) c++;

        boolean[] selected = new boolean[candidatesSize];
        for(int i = candidatesSize - 1; i >= 0; i--) {
            if(taken[i][c]) {
                selected[i] = true;
                c -= (int)amounts[i];
            }
        }

        List<PayoutDecision> decisions = new ArrayList<>(candidatesSize);
        for(int i = 0; i < candidatesSize; i++) {
            decisions.add(new PayoutDecision(candidates.get(i), selected[i]));
        }
        return decisions;
    }

    private static long toTetri(BigDecimal value) {
        return value.movePointRight(2).longValueExact();
    }
}
