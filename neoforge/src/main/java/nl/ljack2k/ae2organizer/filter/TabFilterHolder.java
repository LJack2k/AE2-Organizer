package nl.ljack2k.ae2organizer.filter;

import appeng.api.stacks.AEKey;

import java.util.function.Predicate;

/**
 * Duck-type interface mixed into AE2's client {@code Repo} (see
 * {@code nl.ljack2k.ae2organizer.mixin.RepoMixin}) so we can attach the active
 * tab's filter predicate to a live terminal.
 * <p>
 * Declared in this (non-mixin) package so ordinary code can import it cleanly;
 * the mixin still {@code implements} it. Cast a live {@code Repo} to this at
 * runtime to push a predicate, then call {@code Repo.updateView()}.
 */
public interface TabFilterHolder {
    /**
     * @param predicate the filter to apply to every entry, or {@code null} to
     *                  show everything (the "All" tab).
     */
    void ae2organizer$setTabFilter(Predicate<AEKey> predicate);
}
