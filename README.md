[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/htrc/Metadata-entities-lookup/ci.yml?branch=main)](https://github.com/htrc/Metadata-entities-lookup/actions/workflows/ci.yml)
[![codecov](https://codecov.io/github/htrc/Metadata-bibframe-entities/graph/badge.svg?token=2y6GAtWfnP)](https://codecov.io/github/htrc/Metadata-bibframe-entities)
[![GitHub release (latest SemVer including pre-releases)](https://img.shields.io/github/v/release/htrc/Metadata-entities-lookup?include_prereleases&sort=semver)](https://github.com/htrc/Metadata-entities-lookup/releases/latest)

# Metadata-entities-lookup
Used to perform lookup (resolve) entities via external sources like VIAF, LOC, and WorldCat

# Build
* To generate a package that can be invoked via a shell script, run:  
  `sbt stage`  
  then find the result in `target/universal/stage/` folder.
* To generate a distributable ZIP package, run:  
  `sbt dist`  
  then find the result in `target/universal/` folder.

# Run
```
entities-lookup
  -c, --cache  <FILE>      (Optional) Entity value cache file
  -o, --output  <FILE>     Write the output to FILE
  -p, --parallelism  <N>   (Optional) The number of parallel connections to make
                           (default = 12)
  -h, --help               Show help message
  -v, --version            Show version of this program

 trailing arguments:
  input (required)   The path to the folder containing the entities to look up
```
