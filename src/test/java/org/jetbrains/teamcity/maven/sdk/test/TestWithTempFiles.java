

package org.jetbrains.teamcity.maven.sdk.test;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Created by Nikita.Skvortsov
 * date: 28.03.2014.
 */
public class TestWithTempFiles {
    @NotNull
    @Rule
    public TemporaryFolder myTempFiles = new TemporaryFolder();
}