package org.rust.ide.sdk.remote;

//import com.intellij.execution.ExecutionException;
//import com.intellij.openapi.components.ServiceManager;
//import com.intellij.openapi.project.Project;
//import com.intellij.openapi.projectRoots.Sdk;
//import com.intellij.openapi.util.Key;
//import com.intellij.openapi.util.text.StringUtil;
//import com.intellij.remote.*;
//import com.intellij.remote.ext.CredentialsCase;
//import com.intellij.remote.ext.CredentialsManager;
//import com.intellij.util.Consumer;
//import com.intellij.util.PathMappingSettings;
//import org.jdom.Element;
//import org.jetbrains.annotations.NotNull;
//import org.jetbrains.annotations.Nullable;
//import org.rust.ide.sdk.RsSdkAdditionalData;
//
//import java.io.File;
//import java.util.ArrayList;
//
//public class RsRemoteSdkAdditionalData extends RsSdkAdditionalData implements RsRemoteSdkAdditionalDataBase {
//    private static final String PYCHARM_HELPERS = ".rust_helpers";
//    private static final String SKELETONS_PATH = "SKELETONS_PATH";
//    private static final String VERSION = "VERSION";
//    private static final String RUN_AS_ROOT_VIA_SUDO = "RUN_AS_ROOT_VIA_SUDO";
//
//    private final RemoteConnectionCredentialsWrapper myRemoteConnectionCredentialsWrapper = new RemoteConnectionCredentialsWrapper();
//
//    private final RemoteSdkPropertiesHolder myRemoteSdkProperties = new RemoteSdkPropertiesHolder(PYCHARM_HELPERS);
//
//    private String mySkeletonsPath;
//
//    private String myVersionString;
//
//    public RsRemoteSdkAdditionalData(String interpreterPath) {
//        this(interpreterPath, false);
//    }
//
//    public RsRemoteSdkAdditionalData(String interpreterPath, boolean runAsRootViaSudo) {
//        super(computeFlavor(interpreterPath));
//        setInterpreterPath(interpreterPath);
//        setRunAsRootViaSudo(runAsRootViaSudo);
//    }
//
//    private RemoteSdkCredentialsProducer<RsRemoteSdkCredentials> getProducer() {
//        RsSshInterpreterManager manager = RsSshInterpreterManager.Factory.getInstance();
//        if (manager != null) {
//            return manager.getRemoteSdkCredentialsProducer(credentials -> createRsRemoteSdkCredentials(credentials), myRemoteConnectionCredentialsWrapper);
//        } else {
//            throw new IllegalStateException("No plugin");
//            //TODO:
//        }
//    }
//
//    @Override
//    public RemoteConnectionCredentialsWrapper connectionCredentials() {
//        return myRemoteConnectionCredentialsWrapper;
//    }
//
//    @Nullable
//    private static RsSdkFlavor computeFlavor(@Nullable String sdkPath) {
//        if (sdkPath == null) {
//            return null;
//        }
//        for (RsSdkFlavor flavor : getApplicableFlavors(sdkPath.contains("\\"))) {
//            if (flavor.isValidSdkPath(new File(sdkPath))) {
//                return flavor;
//            }
//        }
//        return null;
//    }
//
//    private static List<RsSdkFlavor> getApplicableFlavors(boolean isWindows) {
//        List<RsSdkFlavor> result = new ArrayList<>();
//        if (isWindows) {
//            result.add(ServiceManager.getService(WinRsSdkFlavor.class));
//        } else {
//            result.add(UnixRsSdkFlavor.INSTANCE);
//        }
//        result.addAll(RsSdkFlavor.getPlatformIndependentFlavors());
//
//        return result;
//    }
//
//    public String getSkeletonsPath() {
//        return mySkeletonsPath;
//    }
//
//    public void setSkeletonsPath(String path) {
//        mySkeletonsPath = path;
//    }
//
//    @Override
//    public <C> void setCredentials(Key<C> key, C credentials) {
//        myRemoteConnectionCredentialsWrapper.setCredentials(key, credentials);
//    }
//
//    @Override
//    public CredentialsType getRemoteConnectionType() {
//        return myRemoteConnectionCredentialsWrapper.getRemoteConnectionType();
//    }
//
//    @Override
//    public void switchOnConnectionType(CredentialsCase... cases) {
//        myRemoteConnectionCredentialsWrapper.switchType(cases);
//    }
//
//    private RsRemoteSdkCredentials createRsRemoteSdkCredentials(@NotNull RemoteCredentials credentials) {
//        RsRemoteSdkCredentialsHolder res = new RsRemoteSdkCredentialsHolder();
//        RemoteSdkCredentialsBuilder.copyCredentials(credentials, res);
//        myRemoteSdkProperties.copyTo(res);
//        res.setSkeletonsPath(getSkeletonsPath());
//        res.setInitialized(isInitialized());
//        res.setValid(isValid());
//        res.setSdkId(getSdkId());
//        return res;
//    }
//
//    @Override
//    public String getInterpreterPath() {
//        return myRemoteSdkProperties.getInterpreterPath();
//    }
//
//    @Override
//    public void setInterpreterPath(String interpreterPath) {
//        myRemoteSdkProperties.setInterpreterPath(interpreterPath);
//    }
//
//    @Override
//    public boolean isRunAsRootViaSudo() {
//        return myRemoteSdkProperties.isRunAsRootViaSudo();
//    }
//
//    @Override
//    public void setRunAsRootViaSudo(boolean runAsRootViaSudo) {
//        myRemoteSdkProperties.setRunAsRootViaSudo(runAsRootViaSudo);
//    }
//
//    @Override
//    public String getHelpersPath() {
//        return myRemoteSdkProperties.getHelpersPath();
//    }
//
//    @Override
//    public void setHelpersPath(String helpersPath) {
//        myRemoteSdkProperties.setHelpersPath(helpersPath);
//    }
//
//    @Override
//    public String getDefaultHelpersName() {
//        return myRemoteSdkProperties.getDefaultHelpersName();
//    }
//
//    @NotNull
//    @Override
//    public PathMappingSettings getPathMappings() {
//        return myRemoteSdkProperties.getPathMappings();
//    }
//
//    @Override
//    public void setPathMappings(@Nullable PathMappingSettings pathMappings) {
//        myRemoteSdkProperties.setPathMappings(pathMappings);
//    }
//
//    @Override
//    public boolean isHelpersVersionChecked() {
//        return myRemoteSdkProperties.isHelpersVersionChecked();
//    }
//
//    @Override
//    public void setHelpersVersionChecked(boolean helpersVersionChecked) {
//        myRemoteSdkProperties.setHelpersVersionChecked(helpersVersionChecked);
//    }
//
//    @Override
//    public void setSdkId(String sdkId) {
//        throw new IllegalStateException("sdkId in this class is constructed based on fields, so it can't be set");
//    }
//
//    @Override
//    public String getSdkId() {
//        return constructSdkID(myRemoteConnectionCredentialsWrapper, myRemoteSdkProperties);
//    }
//
//    public String getPresentableDetails() {
//        return myRemoteConnectionCredentialsWrapper.getPresentableDetails(myRemoteSdkProperties.getInterpreterPath());
//    }
//
//    private static String constructSdkID(@NotNull RemoteConnectionCredentialsWrapper remoteConnectionCredentialsWrapper,
//                                         @NotNull RemoteSdkPropertiesHolder properties) {
//        return remoteConnectionCredentialsWrapper.getId() + properties.getInterpreterPath();
//    }
//
//    @Override
//    public boolean isInitialized() {
//        return myRemoteSdkProperties.isInitialized();
//    }
//
//    @Override
//    public void setInitialized(boolean initialized) {
//        myRemoteSdkProperties.setInitialized(initialized);
//    }
//
//    @Override
//    public boolean isValid() {
//        return myRemoteSdkProperties.isValid();
//    }
//
//    @Override
//    public void setValid(boolean valid) {
//        myRemoteSdkProperties.setValid(valid);
//    }
//
//    @Override
//    @Deprecated
//    public RsRemoteSdkCredentials getRemoteSdkCredentials() throws InterruptedException, ExecutionException {
//        return getProducer().getRemoteSdkCredentials();
//    }
//
//    @Override
//    public RsRemoteSdkCredentials getRemoteSdkCredentials(boolean allowSynchronousInteraction)
//            throws InterruptedException, ExecutionException {
//        return getProducer().getRemoteSdkCredentials(allowSynchronousInteraction);
//    }
//
//    @Override
//    public RsRemoteSdkCredentials getRemoteSdkCredentials(@Nullable Project project, boolean allowSynchronousInteraction)
//            throws InterruptedException,
//            ExecutionException {
//        return getProducer().getRemoteSdkCredentials(allowSynchronousInteraction);
//    }
//
//    public boolean connectionEquals(RsRemoteSdkAdditionalData data) {
//        return myRemoteConnectionCredentialsWrapper.equals(data.myRemoteConnectionCredentialsWrapper);
//    }
//
//    @Override
//    public Object getRemoteSdkDataKey() {
//        return myRemoteConnectionCredentialsWrapper.getConnectionKey();
//    }
//
//    @Override
//    public void produceRemoteSdkCredentials(final @NotNull Consumer<RsRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
//        getProducer().produceRemoteSdkCredentials(remoteSdkCredentialsConsumer);
//    }
//
//    @Override
//    public void produceRemoteSdkCredentials(final boolean allowSynchronousInteraction,
//                                            final @NotNull Consumer<RsRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
//        getProducer().produceRemoteSdkCredentials(allowSynchronousInteraction, remoteSdkCredentialsConsumer);
//    }
//
//    @Override
//    public void produceRemoteSdkCredentials(@Nullable Project project, final boolean allowSynchronousInteraction,
//                                            final @NotNull Consumer<RsRemoteSdkCredentials> remoteSdkCredentialsConsumer) {
//        getProducer().produceRemoteSdkCredentials(allowSynchronousInteraction, remoteSdkCredentialsConsumer);
//    }
//
//    @NotNull
//    @Override
//    public RsRemoteSdkAdditionalData copy() {
//        RsRemoteSdkAdditionalData copy = new RsRemoteSdkAdditionalData(myRemoteSdkProperties.getInterpreterPath(), isRunAsRootViaSudo());
//
//        copyTo(copy);
//
//        return copy;
//    }
//
//    public void copyTo(@NotNull RsRemoteSdkAdditionalData copy) {
//        copy.setSkeletonsPath(mySkeletonsPath);
//        copy.setVersionString(myVersionString);
//        myRemoteSdkProperties.copyTo(copy.myRemoteSdkProperties);
//        myRemoteConnectionCredentialsWrapper.copyTo(copy.myRemoteConnectionCredentialsWrapper);
//    }
//
//    @Override
//    public void save(@NotNull final Element rootElement) {
//        super.save(rootElement);
//
//
//        myRemoteSdkProperties.save(rootElement);
//
//        rootElement.setAttribute(SKELETONS_PATH, StringUtil.notNullize(getSkeletonsPath()));
//        rootElement.setAttribute(VERSION, StringUtil.notNullize(getVersionString()));
//        rootElement.setAttribute(RUN_AS_ROOT_VIA_SUDO, Boolean.toString(isRunAsRootViaSudo()));
//
//        // this should be executed at the end because of the case with UnknownCredentialsHolder
//        myRemoteConnectionCredentialsWrapper.save(rootElement);
//    }
//
//
//    @NotNull
//    public static RsRemoteSdkAdditionalData loadRemote(@NotNull Sdk sdk, @Nullable Element element) {
//        final String path = sdk.getHomePath();
//        assert path != null;
//        final RsRemoteSdkAdditionalData data = new RsRemoteSdkAdditionalData(RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath(path), false);
//        data.load(element);
//
//        if (element != null) {
//            CredentialsManager.getInstance().loadCredentials(path, element, data);
//            data.myRemoteSdkProperties.load(element);
//
//            data.setSkeletonsPath(StringUtil.nullize(element.getAttributeValue(SKELETONS_PATH)));
//            String helpersPath = StringUtil.nullize(element.getAttributeValue("PYCHARM_HELPERS_PATH"));
//            if (helpersPath != null) {
//                data.setHelpersPath(helpersPath);
//            }
//            data.setVersionString(StringUtil.nullize(element.getAttributeValue(VERSION)));
//            data.setRunAsRootViaSudo(StringUtil.equals(element.getAttributeValue(RUN_AS_ROOT_VIA_SUDO), "true"));
//        }
//
//        return data;
//    }
//
//    @Override
//    public void setVersionString(String versionString) {
//        myVersionString = versionString;
//    }
//
//    @Override
//    public String getVersionString() {
//        return myVersionString;
//    }
//}
