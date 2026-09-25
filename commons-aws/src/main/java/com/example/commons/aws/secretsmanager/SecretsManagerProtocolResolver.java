package com.example.commons.aws.secretsmanager;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ProtocolResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Resolves {@value #PROTOCOL}{@code <secret name or ARN>} resource locations to a
 * {@link SecretsManagerResource}, whose content is the raw {@code AWSCURRENT} value of
 * the secret. A location such as {@code commons.security.oauth2.jwks} can then name a
 * secret the same way it names a {@code file:} or {@code classpath:} resource.
 *
 * <p>
 * The same prefix in {@code spring.config.import} is Spring Cloud AWS's config data
 * import, which is unrelated: it flattens a JSON secret's top-level members into
 * configuration properties. Here the prefix only ever means "the secret's value, as is".
 *
 * <p>
 * Registers itself with the application context's {@link ResourceLoader}, as a
 * {@link BeanFactoryPostProcessor} so that it is in place before any other bean resolves
 * a resource. The {@link SecretsManagerClient} is looked up only when a secret is read,
 * so the client auto-configuration may be turned off with
 * {@code spring.cloud.aws.secretsmanager.enabled=false} by an application that only uses
 * {@code file:} or {@code classpath:} locations; reading a {@value #PROTOCOL} location
 * then fails with a message saying so.
 */
public class SecretsManagerProtocolResolver implements ProtocolResolver, BeanFactoryPostProcessor, ResourceLoaderAware {

	/**
	 * The location prefix this resolver handles.
	 */
	public static final String PROTOCOL = "aws-secretsmanager:";

	static final String CLIENT_UNAVAILABLE_MESSAGE = "No SecretsManagerClient is available to read a " + PROTOCOL
			+ " location; check that spring.cloud.aws.secretsmanager.enabled is not false";

	private BeanFactory beanFactory;

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		if (resourceLoader instanceof DefaultResourceLoader defaultResourceLoader
				&& !defaultResourceLoader.getProtocolResolvers().contains(this)) {
			defaultResourceLoader.addProtocolResolver(this);
		}
	}

	@Override
	public Resource resolve(String location, ResourceLoader resourceLoader) {
		if (!location.startsWith(PROTOCOL)) {
			return null;
		}
		return new SecretsManagerResource(location.substring(PROTOCOL.length()), this::client);
	}

	private SecretsManagerClient client() {
		SecretsManagerClient client = (this.beanFactory != null)
				? this.beanFactory.getBeanProvider(SecretsManagerClient.class).getIfAvailable() : null;
		if (client == null) {
			throw new IllegalStateException(CLIENT_UNAVAILABLE_MESSAGE);
		}
		return client;
	}

}
