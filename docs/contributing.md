# Contributing

* Star the project on [GitHub](https://github.com/testcontainers/testcontainers-java) and help spread the word :)
* Join our [Slack workspace](http://slack.testcontainers.org)
* [Start a discussion](https://github.com/testcontainers/testcontainers-java/discussions) if you have an idea, find a possible bug or have a general question.
* Contribute improvements or fixes using a [Pull Request](https://github.com/testcontainers/testcontainers-java/pulls). If you're going to contribute, thank you! Please just be sure to:
    * discuss with the authors prior to doing anything big.
    * follow the style, naming and structure conventions of the rest of the project.
    * make commits atomic and easy to merge.
    * when updating documentation, please see [our guidance for documentation contributions](contributing_docs.md).
    * apply format running `./gradlew spotlessApply` (this requires [Node.js](https://nodejs.org/) to be installed on your machine, one of the [package managers](https://nodejs.org/en/download/package-manager/) might be handy)
    * verify all tests are passing. Build the project with `./gradlew check` to do this.
    **N.B.** Gradle's Build Cache is enabled by default, but you can add `--no-build-cache` flag to disable it.

## Contributing new modules

We often receive proposals (or fully formed PRs) for new modules.
We're very happy to have contributions, but new modules require specific extra care. We want to balance:

* Usefulness of the module.
* Our ability to support the module in the future, potentially after contributors have moved on.
* Contributors time, so that nobody puts in wasted effort.

### Does it need to be a module?

*N.B. this is not a perfect list - please always reach out to us before starting on a module contribution!*

* Does the module enable use of Testcontainers with a popular or rapidly growing technology?
* Does the module 'add value' beyond a `GenericContainer` code snippet/example? e.g.
    * does it neatly encapsulate a difficult problem of running the program in a container?
    * does it add technology-specific [wait strategies](features/startup_and_waits.md)?
    * does it enable straightforward usage of client libraries?

If the answers to the above are all yes, then a new module may be a good approach.

Otherwise, it is entirely possible for you to:

* publish a code snippet
* contribute an example to the Testcontainers repo
* publish your own third party library

In any case, please contact us to help validate your proposal!

### Checklist

*Suggestion: copy and paste this list into PRs for new modules.*

Every item on this list will require judgement by the Testcontainers core maintainers. Exceptions will sometimes be possible; items with `should` are more likely to be negotiable than those items with `must`.

#### Default docker image

- [ ] Should be a Docker Hub official image, or published by a reputable source (ideally the company or organisation that officially supports the technology)
- [ ] Should have a verifiable open source Dockerfile and a way to view the history of changes
- [ ] MUST show general good practices regarding container image tagging - e.g. we do not use `latest` tags, and we do not use tags that may be mutated in the future
- [ ] MUST be legal for Testcontainers developers and Testcontainers users to pull and use. Mechanisms exist to allow EULA acceptance to be signalled, but images that can be used without a licence are greatly preferred.

#### Module dependencies

- [ ] The module should use as few dependencies as possible,
- [ ] Regarding libraries, either:
    - they should be `compileOnly` if they are likely to already be on the classpath for users' tests (e.g. client libraries or drivers)
    - they can be `implementation` (and thus transitive dependencies) if they are very unlikely to conflict with users' dependencies.
- [ ] If client libraries are used to test or use the module, these MUST be legal for Testcontainers developers and Testcontainers users to download and use.

#### API (e.g. `MyModuleContainer` class)

- [ ] Favour doing the right thing, and least surprising thing, by default
- [ ] Ensure that method and parameter names are easy to understand. Many users will ignore documentation, so IDE-based substitutes (autocompletion and Javadocs) should be intuitive.
- [ ] The module's public API should only handle standard JDK data types and MUST not expose data types that come from `compileOnly` dependencies. This is to reduce the risk of compatibility problems with future versions of third party libraries.
#### Documentation

- [ ] Every module MUST have a dedicated documentation page containing:
    - [ ] A high level overview
    - [ ] A usage example
    - [ ] If appropriate, basic API documentation or further usage guidelines
    - [ ] Dependency information
    - [ ] Acknowledgements, if appropriate
- [ ] Consider that many users will not read the documentation pages - even if the first person to add it to a project does, people reading/updating the code in the future may not. Try and avoid the need for critical knowledge that is only present in documentation.



### Incubating modules

We have a policy of marking new modules as 'incubating' so that we can evaluate its maintainability and usability traits over a longer period of time.
We currently believe 3 months is a fair period of time, but may change this.

New modules should have the following warning at the top of their documentation pages:

!!! note
    This module is INCUBATING. While it is ready for use and operational in the current version of Testcontainers, it is possible that it may receive breaking changes in the future. See [our contributing guidelines](/contributing/#incubating-modules) for more information on our incubating modules policy.

We will evaluate incubating modules periodically, and remove the label when appropriate.


## Combining Dependabot PRs

Since we generally get a lot of Dependabot PRs, we regularly combine them into single commits.
For this, we are using the [gh-combine-prs](https://github.com/rnorth/gh-combine-prs) extension for [GitHub CLI](https://cli.github.com/).

The whole process is as follows:

1. Check that all open Dependabot PRs did succeed their build. If they did not succeed, trigger a rerun if the cause were external factors or else document the reason if obvious.
2. Run the extension from an up-to-date local `main` branch: `gh combine-prs --query "author:app/dependabot"`
3. Merge conflicts might appear. Just ignore them, we will get those PRs in a future run.
4. Once the build of the combined PR did succeed, temporarily enable merge commits and merge the PR using a merge commit through the GitHub UI.
5. After the merge, disable merge commits again.
