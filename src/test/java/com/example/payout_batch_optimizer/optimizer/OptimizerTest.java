package com.example.payout_batch_optimizer.optimizer;

import com.example.payout_batch_optimizer.exception.BatchTooLargeException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimizerTest {

    private final Optimizer optimizer = new Optimizer();

    @Test
    void specExample_selectsPo3002AndPo3004() {
        List<PayoutCandidate> candidates = List.of(
                candidate("PO-3001", "4000", "90"),
                candidate("PO-3002", "6000", "150"),
                candidate("PO-3003", "2500", "55"),
                candidate("PO-3004", "5000", "115"));

        List<PayoutDecision> decisions = optimize(candidates, "12000");

        assertThat(selectedReferences(decisions)).containsExactly("PO-3002", "PO-3004");
        assertThat(totalCommission(decisions)).isEqualByComparingTo("265");
        assertThat(totalAmount(decisions)).isEqualByComparingTo("11000");
    }

    @Test
    void greedyCounterexample_prefersTwoSmallerPayoutsOverBestRatio() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "4", "3"),
                candidate("B", "3", "2"),
                candidate("C", "3", "2"));

        List<PayoutDecision> decisions = optimize(candidates, "6");

        assertThat(selectedReferences(decisions)).containsExactly("B", "C");
        assertThat(totalCommission(decisions)).isEqualByComparingTo("4");
    }

    @Test
    void exactFit_usesTheWholeFloat() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "7", "10"),
                candidate("B", "3", "5"),
                candidate("C", "6", "9"));

        List<PayoutDecision> decisions = optimize(candidates, "10");

        assertThat(selectedReferences(decisions)).containsExactly("A", "B");
        assertThat(totalAmount(decisions)).isEqualByComparingTo("10");
        assertThat(totalCommission(decisions)).isEqualByComparingTo("15");
    }

    @Test
    void everythingFits_selectsAll() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "10", "1"),
                candidate("B", "20", "2"),
                candidate("C", "30", "3"));

        List<PayoutDecision> decisions = optimize(candidates, "100");

        assertThat(selectedReferences(decisions)).containsExactly("A", "B", "C");
    }

    @Test
    void nothingFits_selectsNothing() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "500", "50"),
                candidate("B", "600", "60"));

        List<PayoutDecision> decisions = optimize(candidates, "100");

        assertThat(selectedReferences(decisions)).isEmpty();
        assertThat(totalCommission(decisions)).isEqualByComparingTo("0");
    }

    @Test
    void zeroFloat_selectsNothing() {
        List<PayoutDecision> decisions = optimize(List.of(candidate("A", "10", "5")), "0");

        assertThat(selectedReferences(decisions)).isEmpty();
    }

    @Test
    void singleCandidateThatFits_isSelected() {
        List<PayoutDecision> decisions = optimize(List.of(candidate("A", "50", "5")), "100");

        assertThat(selectedReferences(decisions)).containsExactly("A");
    }

    @Test
    void singleCandidateThatDoesNotFit_isNotSelected() {
        List<PayoutDecision> decisions = optimize(List.of(candidate("A", "150", "5")), "100");

        assertThat(selectedReferences(decisions)).isEmpty();
    }

    @Test
    void tieBreak_equalCommissionPrefersLessFloat() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "10", "5"),
                candidate("B", "4", "5"));

        List<PayoutDecision> decisions = optimize(candidates, "10");

        assertThat(selectedReferences(decisions)).containsExactly("B");
        assertThat(totalAmount(decisions)).isEqualByComparingTo("4");
    }

    @Test
    void zeroCommission_isNeverSelected() {
        List<PayoutDecision> decisions = optimize(List.of(candidate("A", "5", "0")), "10");

        assertThat(selectedReferences(decisions)).isEmpty();
    }

    @Test
    void eachRequestIsUsedAtMostOnce() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "3", "2"),
                candidate("B", "4", "3"));

        List<PayoutDecision> decisions = optimize(candidates, "6");

        assertThat(selectedReferences(decisions)).containsExactly("B");
        assertThat(totalCommission(decisions)).isEqualByComparingTo("3");
    }

    @Test
    void decimalAmounts_exactFitSelectsBoth() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "100.01", "5"),
                candidate("B", "99.99", "4"));

        List<PayoutDecision> decisions = optimize(candidates, "200.00");

        assertThat(selectedReferences(decisions)).containsExactly("A", "B");
    }

    @Test
    void decimalAmounts_oneCentShortSelectsOnlyTheBetterOne() {
        List<PayoutCandidate> candidates = List.of(
                candidate("A", "100.01", "5"),
                candidate("B", "99.99", "4"));

        List<PayoutDecision> decisions = optimize(candidates, "199.99");

        assertThat(selectedReferences(decisions)).containsExactly("A");
    }

    @Test
    void decisionsKeepInputOrder() {
        List<PayoutCandidate> candidates = List.of(
                candidate("PO-1", "5", "1"),
                candidate("PO-2", "50", "1"),
                candidate("PO-3", "5", "1"));

        List<PayoutDecision> decisions = optimize(candidates, "10");

        assertThat(decisions).extracting(d -> d.payoutCandidate().requestReference())
                .containsExactly("PO-1", "PO-2", "PO-3");
        assertThat(decisions).extracting(PayoutDecision::selected)
                .containsExactly(true, false, true);
    }

    @Test
    void tooLargeProblem_isRejected() {
        List<PayoutCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate("PO-" + i, "1000000.00", "10"));
        }

        assertThatThrownBy(() -> optimizer.optimizePayoutBatch(candidates, new BigDecimal("5000000.00")))
                .isInstanceOf(BatchTooLargeException.class);
    }

    @Test
    void tooLargeProblem_maxFloatWithManyRequests_isRejected() {
        List<PayoutCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            candidates.add(candidate("PO-" + i, "1.00", "1"));
        }

        assertThatThrownBy(() -> optimizer.optimizePayoutBatch(candidates, new BigDecimal("999999999999.99")))
                .isInstanceOf(BatchTooLargeException.class);
    }

    private List<PayoutDecision> optimize(List<PayoutCandidate> candidates, String availableFloatText) {
        BigDecimal availableFloat = new BigDecimal(availableFloatText);
        List<PayoutDecision> decisions = optimizer.optimizePayoutBatch(candidates, availableFloat);

        assertThat(decisions).hasSize(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            assertThat(decisions.get(i).payoutCandidate()).isSameAs(candidates.get(i));
        }
        assertThat(totalAmount(decisions)).isLessThanOrEqualTo(availableFloat);

        return decisions;
    }

    private static PayoutCandidate candidate(String reference, String amount, String commission) {
        return new PayoutCandidate(reference, new BigDecimal(amount), new BigDecimal(commission));
    }

    private static List<String> selectedReferences(List<PayoutDecision> decisions) {
        return decisions.stream()
                .filter(PayoutDecision::selected)
                .map(d -> d.payoutCandidate().requestReference())
                .toList();
    }

    private static BigDecimal totalAmount(List<PayoutDecision> decisions) {
        return decisions.stream()
                .filter(PayoutDecision::selected)
                .map(d -> d.payoutCandidate().payoutAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal totalCommission(List<PayoutDecision> decisions) {
        return decisions.stream()
                .filter(PayoutDecision::selected)
                .map(d -> d.payoutCandidate().agentCommission())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}