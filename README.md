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
libraryDependencies += "io.github.sungiant" %% "zenith" % "0.0.5"
```

Additionally, the following packages contain off the shelf implementations of Zenith's abstract network layer and sequencing context:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith-netty" % "0.0.5"
libraryDependencies += "io.github.sungiant" %% "zenith-context" % "0.0.5"
```

## Architecture

Zenith is achitected around both a generic network layer and a generic sequencing context.  This make it possible to write a web service using Zenith that allows for easily changing both the choice of network layer implementation (Netty, Akka HTTP...) and the choice of sequencing context (Scala Future, Twitter Future, Akka Future + WriterT Monad Transformer) simply by swapping in and out modules.

Why is Zenith's generic architecture cool?  It makes it easy to write a webservice against Zenith with the dependences you want, if you decide that you want your project to only have dependencies on Akka HTTP, you can do that, simply switch out the `zenith-netty` package for your own implementation of Zenith's abstract network layer using Akka HTTP.  Zenith doesn't impose such choices upon your project. 

Zenith is written in a functional style; the codebase does not include a single instance of Scala's `var` keyword.  The core part of Zenith is built atop:

 * [cats](https://github.com/non/kind-projector) for functional patterns
 * [simulacrum](https://github.com/mpilquist/simulacrum) for minimizing typeclass boilerplate
 * [circe](https://github.com/typelevel/machinist) for functional JSON
 * ...and of course a pure functional subset of the Scala language.

## Alternatives

* [unfiltered](http://unfiltered.databinder.net/Unfiltered.html) a toolkit for servicing HTTP requests

## License

Zenith is licensed under the **[MIT License][mit]**; you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[mit]: https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE
