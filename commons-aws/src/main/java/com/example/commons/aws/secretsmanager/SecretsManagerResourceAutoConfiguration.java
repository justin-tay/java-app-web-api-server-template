package com.example.commons.aws.secretsmanager;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Lets resource locations name an AWS Secrets Manager secret as
 * {@code aws-secretsmanager:<secret name or ARN>}; see
 * {@link SecretsManagerProtocolResolver}.
 *
 * <p>
 * The {@link SecretsManagerClient} itself, with its region and credentials, comes from
 * Spring Cloud AWS's own auto-configuration and {@code spring.cloud.aws.*} properties.
 * Leave the credentials to the AWS SDK default chain (an ECS task role, EKS Pod Identity,
 * and so on); see docs/adr/0020.
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

}
