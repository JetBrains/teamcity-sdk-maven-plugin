[![team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

TeamCity SDK Maven plugin
=========================

## Quickstart

 (Almost) quickstart on developing a plugin is available [here](https://github.com/nskvortsov/teamcity-sdk-maven-plugin/wiki/Developing-TeamCity-plugin)

## General Info

This Maven plugin allows controlling a TeamCity instance from the command line. It will install a developed plugin if it is available. 

The plugin works best when used with projects generated from [maven archetype](http://confluence.jetbrains.com/display/TCDL/Developing+Plugins+Using+Maven#DevelopingPluginsUsingMaven-MavenArchetypes) for TeamCity plugins.

If you have such a project, you can skip to [plugin goals](#plugin-goals)

Otherwise, make sure you have the proper plugin repository configured:
```xml
<pluginRepositories>
  <pluginRepository>
    <id>JetBrains</id>
    <url>http://download.jetbrains.com/teamcity-repository</url>
  </pluginRepository>
</pluginRepositories>
```
and add the plugin itself:

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
to the root pom.xml file and run

```mvn package tc-sdk:start```

You will get:
* A Server with your plugin and debug port 10111
* An Agent with your plugin and debug port  10112

### Plugin goals

The plugin adds the following goals:

* ```mvn tc-sdk:init``` will check if TeamCity is available in the target location and its version is the same as used in the maven project. If it is missing, the plugin can download the distribution and unpack it for you.
* ```mvn tc-sdk:start``` will do the init check (see above), deploy your plugin to the data directory, and start a TeamCity server and agent
* ```mvn tc-sdk:stop``` will do the init check (once again) and will issue a stop command to both the server and agent.
* ```mvn tc-sdk:reload``` will do the init check and will copy your plugin to the data directory. Can be useful to quickly deploy agent-side changes without the need to restart the whole server, as TeamCity will automatically update the agent with the new plugin version.
* ```mvn tc-sdk:reloadResources``` will do the init check and will copy over your static resources (from <plugin>-server/src/main/resouces/buildServerResources) to target teamcity server. May speedup ui development.

Please note that TeamCity startup process is not instant and the stop command sent immediately after the start may not be processed properly.

### Plugin settings

The plugin is highly configurable. See the list of options below, along with the default values. "User properties" are used to pass values from the command line (e.g., ```mvn tc-sdk:init -DteamcityVersion=10.0```)

- ```teamcityDir```	path to the TeamCity installation. A relative path will be resolved against ```${project.baseDir}```
 - Default value is: servers/${teamcity-version}
 - User property is: teamcityDir

- ```dataDirectory``` 	path to the data directory to be used with TeamCity. A relative path will be resolved against the TeamCity installation path.
 - Default value is: .datadir
 - User property is: teamcityDataDir

- ```teamcityVersion``` TeamCity version, e.g. 10.0
 - Default value is: ${teamcity-version}
 - User property is: teamcityVersion

- ```serverDebugStr``` 	additional options that will be passed to the TeamCity server on startup. Customize this property if you want to change the debug port or other values
 - Default value is: -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10111 -Dteamcity.development.mode=true
 - User property is: serverDebugStr

- ```agentDebugStr``` 	additional options that will be passed to the TeamCity agent on startup. Customize this property if you want to change the debug port or other values
 - Default value is: -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=10112
 - User property is: agentDebugStr

- ```downloadQuietly``` do not ask the user and download TeamCity without any notifications
 - Default value is: false
 - User property is: downloadQuietly

- ```teamcitySourceURL``` base part of the URL that will be used to download the TeamCity distribution. The plugin will append "/TeamCity-<version>.tar.gz" to it (e.g. http://download.jetbrains.com/teamcity/TeamCity-10.0.tar.gz)
 - Default value is: http://download.jetbrains.com/teamcity
 - User property is: teamcitySourceURL

- ```startAgent``` the option to start a TeamCity build agent
 - Default value is: true
 - User property is: startAgent

