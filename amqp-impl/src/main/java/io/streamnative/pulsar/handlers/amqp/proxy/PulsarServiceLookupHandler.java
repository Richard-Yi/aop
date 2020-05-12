/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamnative.pulsar.handlers.amqp.proxy;

import io.streamnative.pulsar.handlers.amqp.AmqpProtocolHandler;
import java.net.URL;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pulsar.broker.PulsarService;
import org.apache.pulsar.broker.loadbalance.LoadManager;
import org.apache.pulsar.common.naming.NamespaceBundle;
import org.apache.pulsar.common.naming.NamespaceBundles;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.policies.data.loadbalancer.ServiceLookupData;

/**
 * The proxy start with broker, use this lookup handler to find broker.
 */
@Slf4j
public class PulsarServiceLookupHandler implements LookupHandler {

    private PulsarService pulsarService;

    PulsarServiceLookupHandler(PulsarService pulsarService) {
        this.pulsarService = pulsarService;
    }

    @Override
    public Pair<String, Integer> findBroker(NamespaceName namespaceName) throws ProxyException {
        String hostname = null;
        Integer port = null;

        try {
            int httpPort = 0;
            NamespaceBundles bundles = pulsarService.getNamespaceService()
                    .getNamespaceBundleFactory().getBundles(namespaceName);
            NamespaceBundle bundle = bundles.getFullBundle();

            // The findBrokerServiceUrl method is private
//            pulsarService.getNamespaceService().findBrokerServiceUrl(bundle, false, false).get();

            Optional<URL> url =  pulsarService.getNamespaceService()
                    .getWebServiceUrl(bundle, false, false, false);
            if (url.isPresent()) {
                hostname = url.get().getHost();
                httpPort = url.get().getPort();
            }

            Optional<? extends ServiceLookupData> serviceLookupData = pulsarService.getLocalZkCache().getData(
                    LoadManager.LOADBALANCE_BROKERS_ROOT + "/" + hostname + ":" + httpPort,
                    pulsarService.getLoadManager().get().getLoadReportDeserializer());
            if (serviceLookupData.isPresent()) {
                Optional<String> protocolAdvertise = serviceLookupData.get().getProtocol(AmqpProtocolHandler.PROTOCOL_NAME);
                if (protocolAdvertise.isPresent()) {
                    String advertise = protocolAdvertise.get();
                    String[] splits = advertise.split(":");
                    port = Integer.parseInt(splits[splits.length - 1]);
                }
            } else {
                throw new ProxyException("Failed to find broker for namespaceName: " + namespaceName.toString());
            }
            return Pair.of(hostname, port);
        } catch (Exception e) {
            String errorMsg = String.format("Failed to find broker for namespaceName: %S. msg: %S",
                    namespaceName.toString(), e.getMessage());
            log.error(errorMsg, e);
            throw new ProxyException(errorMsg);
        }
    }

}
