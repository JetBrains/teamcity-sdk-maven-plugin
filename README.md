TeamCity SDK Maven plugin
=========================
[![Build Status](https://travis-ci.org/nskvortsov/teamcity-sdk-maven-plugin.svg?branch=master)](https://travis-ci.org/nskvortsov/teamcity-sdk-maven-plugin)

## Quickstart

 (Almost) quickstart on developing a plugin is available [here](https://github.com/nskvortsov/teamcity-sdk-maven-plugin/wiki/Developing-TeamCity-plugin)

## General Info

This Maven plugin allow to control TeamCity instance from command line. It will install developed plugin if it is available. 

The plugin works best when used with projects generated from [maven archetype](http://confluence.jetbrains.com/display/TCD8/Developing+Plugins+Using+Maven#DevelopingPluginsUsingMaven-MavenArchetypes) for TeamCity plugins.

If you have such a project, add:
```xml
 <build>
    <plugins>
      <plugin>
        <groupId>org.jetbrains.teamcity</groupId>
        <artifactId>teamcity-sdk-maven-plugin</artifactId>
        <version>0.2</version>
        <configuration>
            <teamcityDir>/path/to/tc/distro</teamcityDir> <!-- optional -->
        </configuration>
      </plugin>
    </plugins>
  </build>
```
to root pom.xml file and run

```mvn package tc-sdk:start```

You will get:
* Server with your plugin and debug port 10111
* Agent with your plugin and debug port  10112

### Plugin goals

The plugin adds three simple goadls:

* ```mvn tc-sdk:init``` will check if TeamCity distribution is available in target location and it's version is same as used in the maven project. If the distribution is missing, the plugin can download and unpack it for you.
* ```mvn tc-sdk:start``` will do the init check (see above), deploy your plugin to the distribution, and start TeamCity server and agent
* ```mvn tc-sdk:stop``` will do the init check (once again) and will issue stop command to both server and agent.
* ```mvn tc-sdk:reload``` will do the init check and will copy your plugin to the distribution. Can be useful to quickly deploy agent-side changes without need to restart the whole server, as TeamCity will automatically update the agent with new plugin version.
* ```mvn tc-sdk:reloadResouces``` will do the init check and will copy over your static resources (from <plugin>-server/src/main/resouces/buildServerResources) to target teamcity server. May speedup ui development.

Please note, that TeamCity's startup process is not instant and stop command sent immediately after start may not be processed properly.

### Plugin settings

The plugin is highly configurable. See the list of options below, along with default values. "User properties" are used to pass values from commandline (e.g., ```mvn tc-sdk:init -DteamcityVersion=8.1.1```)

- ```teamcityDir```	path to TeamCity installation. Relative path will be resolved against ```${project.baseDir}```
 - Default value is: servers/${teamcity-version}
 - User property is: teamcityDir

- ```dataDirectory``` 	path to data directory to use with TeamCity. Relative path will be resolved against TeamCity installation path.
 - Default value is: .datadir
 - User property is: teamcityDataDir

- ```teamcityVersion``` TeamCity version, e.g. 8.0
 - Default value is: ${teamcity-version}
 - User property is: teamcityVersion

- ```serverDebugStr``` 	additional options that will be passed to TeamCity server on startup. Customize this property if you want to change debug port or other values
 - Default value is: -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10111 -Dteamcity.development.mode=true
 - User property is: serverDebugStr

- ```agentDebugStr``` 	additional options that will be passed to TeamCity agent on startup. Customize this property if you want to change debug port or other values
 - Default value is: -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10112
 - User property is: agentDebugStr

- ```downloadQuietly``` do not ask user and download TeamCity without any notifications
 - Default value is: false
 - User property is: downloadQuietly

- ```teamcitySourceURL``` base part of URL that will be used to download TeamCity distribution. Plugin will append "/TeamCity-<version>.tar.gz" to it (e.g. http://download.jetbrains.com/teamcity/TeamCity-8.1.tar.gz)
 - Default value is: http://download.jetbrains.com/teamcity
 - User property is: teamcitySourceURL

