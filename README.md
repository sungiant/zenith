# Zenith

[![Build Status](https://travis-ci.org/sungiant/zenith.png?branch=master)][travis]
[![Join the chat at https://gitter.im/sungiant/zenith](https://img.shields.io/badge/gitter-join%20chat-green.svg)][gitter]
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)][license]
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sungiant/zenith_2.12.svg)][maven]
[![Coverage status](https://img.shields.io/codecov/c/github/sungiant/zenith/master.svg)][coverage]

Zenith is a functional HTTP toolkit for Scala.

## Getting started

Zenith is currently available for Scala 2.11.11 and 2.12.3.

To get started with SBT, simply add the following to your `build.sbt` file:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith" % "0.4.4"
```

Additionally, the `zenith-netty` package contains an off the shelf implementation of Zenith's abstract network layer:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith-netty" % "0.4.4"
```

Finally the `zenith-default` package is great for getting started, it contains an the shelf implementation of a Zenith compatible sequencing context as well as some useful Zenith plugins:

```scala
libraryDependencies += "io.github.sungiant" %% "zenith-default" % "0.4.4"
```


## A working example

A demo project can be found in the orphan [demo][demo] branch of this repository.


## Architecture

Zenith is achitected around an abstract network layer and a generic sequencing context.  This make it possible to write a web service using Zenith that allows for easily changing both the network layer implementation (Netty, Akka HTTP...) and the sequencing context (Scala Future, Twitter Future, Akka Future + WriterT Monad Transformer) that binds operations together.

Zenith makes it easy to write a webservice against exactly the dependences you want, if you decide that you want your project to only have dependencies on Akka HTTP, you can do that, simply switch out the `zenith-netty` package for your own implementation of Zenith's abstract network layer using Akka HTTP.  Zenith doesn't impose such choices upon your project. 

Zenith is written in a functional style; the codebase does not include a single instance of Scala's `var` keyword.

The core part of Zenith, the package `zenith`, depends upon:

 * [cats][cats] for functional patterns
 * [simulacrum][simulacrum] for minimizing typeclass boilerplate
 * [nscala-time][nscala-time] for Joda Time

An implementation of Zenith's abstract network layer is provided in a seperate package `zenith-netty` that additionally depends upon:

* [netty][netty] an event-driven asynchronous network application framework


An implementation of a Zenith compatible sequencing context and a handful of useful plugins are, again, provided in a seperate package `zenith-default` that additionally depends upon:

 * [circe][circe] for functional JSON



## Alternatives

* [unfiltered][unfiltered] a toolkit for servicing HTTP requests
* [finch][finch] a combinator library for building Finagle HTTP services

## License

Zenith is licensed under the **[MIT License][license]**; you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[netty]: https://github.com/netty/netty
[nscala-time]: https://github.com/nscala-time/nscala-time
[travis]: https://travis-ci.org/sungiant/zenith
[gitter]: https://gitter.im/sungiant/zenith?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
[maven]: https://maven-badges.herokuapp.com/maven-central/io.github.sungiant/zenith_2.12
[license]: https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE
[coverage]: https://codecov.io/github/sungiant/zenith
[unfiltered]: http://unfiltered.databinder.net/Unfiltered.html
[circe]: https://github.com/travisbrown/circe
[simulacrum]: https://github.com/mpilquist/simulacrum
[cats]: https://github.com/typelevel/cats
[demo]: https://github.com/sungiant/zenith/tree/demo
[finch]: https://github.com/finagle/finch
