// Several tests in this assembly mutate process-global state such as
// environment variables (e.g. TYPECAST_API_KEY). xUnit runs test
// classes in parallel by default, which races those mutations and
// produces flaky results. Disable test parallelization for the whole
// assembly so env-var-touching tests run serially. The unit suite is
// small (~231 tests) and runs in well under a second, so the
// performance impact is negligible.
[assembly: Xunit.CollectionBehavior(DisableTestParallelization = true)]
