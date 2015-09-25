## Zenith

[![Build Status](https://travis-ci.org/sungiant/zenith.png?branch=master)](https://travis-ci.org/sungiant/zenith)
[![Join the chat at https://gitter.im/sungiant/zenith](https://img.shields.io/badge/gitter-join%20chat-green.svg)](https://gitter.im/sungiant/zenith?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sungiant/zenith_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.sungiant/zenith_2.11)
[![Coverage status](https://img.shields.io/codecov/c/github/sungiant/zenith/master.svg)](https://codecov.io/github/sungiant/zenith)

Zenith is a functional HTTP toolkit for Scala.

## Getting Started

Zenith is currently available for Scala 2.11.

To get started with SBT, simply add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith" % "0.0.4"
```

Additionally, the following packages contain off the shelf implementations of Zenith's abstract network layer and sequencing context:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith-netty" % "0.0.4"
libraryDependencies += "io.github.sungiant" %% "zenith-context" % "0.0.4"
```


## License

Zenith is licensed under the **[MIT License][mit]**; you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[mit]: https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE
