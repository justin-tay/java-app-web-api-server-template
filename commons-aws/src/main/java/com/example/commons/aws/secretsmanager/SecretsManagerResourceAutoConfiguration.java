package com.example.commons.aws.secretsmanager;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Lets resource locations name an AWS Secrets Manager secret as
 * {@code aws-secretsmanager:<secret name or ARN>}; see
 * {@link SecretsManagerProtocolResolver}.
 *
 * <p>
 * Unless the application defines its own {@link SecretsManagerClient}, one is created
 * with the AWS SDK default region and credentials chain (an ECS task role, EKS Pod
 * Identity, and so on); see docs/adr/0020. It is lazy, so an application that only uses
 * {@code file:} or {@code classpath:} locations never builds it.
 */
@AutoConfiguration
@ConditionalOnClass(SecretsManagerClient.class)
public class SecretsManagerResourceAutoConfiguration {

	/**
	 * Registers the {@code aws-secretsmanager:} protocol resolver. Static because it is a
	 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}.
	 * @return the protocol resolver
	 */
	@Bean
	static SecretsManagerProtocolResolver secretsManagerProtocolResolver() {
		return new SecretsManagerProtocolResolver();
	}

	/**
	 * The default client, created when the first secret is read.
	 * @return the client
	 */
	@Bean
	@Lazy
	@ConditionalOnMissingBean
	SecretsManagerClient secretsManagerClient() {
		return SecretsManagerClient.create();
	}

}
