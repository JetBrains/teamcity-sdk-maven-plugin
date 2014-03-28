TeamCity SDK Maven plugin
=========================

# General Info

This Maven plugin works best when used with projects generated from [maven archetype](http://confluence.jetbrains.com/display/TCD8/Developing+Plugins+Using+Maven#DevelopingPluginsUsingMaven-MavenArchetypes) for TeamCity plugins.

If you have such a project, add:
```xml
 <build>
    <plugins>
      <plugin>
        <groupId>org.jetbrains.teamcity</groupId>
        <artifactId>teamcity-sdk-maven-plugin</artifactId>
        <version>0.1-SNAPSHOT</version>
        <configuration>
            <teamcityDir>/path/to/tc/distro</teamcityDir> <!-- optional -->
        </configuration>
      </plugin>
    </plugins>
  </build>
```
to root pom.xml file and run

```mvn package teamcity-sdk:start```

You will get:
* Server with your plugin and debug port 10111
* Agent with your plugin and debug port  10112

# Plugin goals

The plugin adds three simple goadls:

* ```mvn teamcity-sdk:init``` will check if TeamCity distribution is available in target location and it's version is same as used in the maven project. If the distribution is missing, the plugin can download and unpack it for you.
* ```mvn teamcity-sdk:start``` will do the init check (see above), deploy your plugin to the distribution, and start TeamCity server and agent
* ```mvn teamcity-sdk:stop``` will do the init check (once again) and will issue stop command to both server and agent.

Please note, that TeamCity's startup process is not instant and stop command sent immediately after start may not be processed properly.

# Plugin settings

