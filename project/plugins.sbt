addSbtPlugin("com.47deg"         % "sbt-microsites"  % "0.9.7")
addSbtPlugin("com.github.gseitz" % "sbt-release"     % "1.0.12")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"         % "2.0.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-osgi"        % "0.9.5")
addSbtPlugin("io.spray"          % "sbt-boilerplate" % "0.6.1")
addSbtPlugin("org.scalariform"   % "sbt-scalariform" % "1.8.3")
addSbtPlugin("org.scoverage"     % "sbt-coveralls"   % "1.2.7")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"   % "1.6.0")
addSbtPlugin("org.tpolecat"      % "tut-plugin"      % "0.6.13")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype"    % "3.8")

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.29"
