package org.hivedb.services;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.apache.cxf.Bus;
import org.apache.cxf.BusException;
import org.apache.cxf.BusFactory;
import org.apache.cxf.aegis.databinding.AegisDatabinding;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.binding.soap.SoapBindingFactory;
import org.apache.cxf.binding.soap.SoapTransportFactory;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.local.LocalTransportFactory;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.WSDLManagerImpl;
import org.hibernate.shards.strategy.access.SequentialShardAccessStrategy;
import org.hivedb.Hive;
import org.hivedb.HiveReadOnlyException;
import org.hivedb.HiveRuntimeException;
import org.hivedb.Schema;
import org.hivedb.configuration.EntityConfig;
import org.hivedb.configuration.EntityHiveConfig;
import org.hivedb.hibernate.BaseDataAccessObject;
import org.hivedb.hibernate.ConfigurationReader;
import org.hivedb.hibernate.DataAccessObject;
import org.hivedb.hibernate.HiveSessionFactory;
import org.hivedb.hibernate.HiveSessionFactoryBuilderImpl;
import org.hivedb.management.HiveInstaller;
import org.hivedb.meta.Node;
import org.hivedb.util.Lists;
import org.hivedb.util.database.HiveDbDialect;
import org.hivedb.util.database.test.H2TestCase;
import org.hivedb.util.database.test.Temperature;
import org.hivedb.util.database.test.WeatherEvent;
import org.hivedb.util.database.test.WeatherReport;
import org.hivedb.util.database.test.WeatherSchema;
import org.hivedb.util.functional.Filter;
import org.hivedb.util.functional.Unary;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DataGenerationServiceTest extends H2TestCase {
	private static Hive hive;
	private EntityHiveConfig config;
	private HiveSessionFactory factory;

	@Test
	public void testGeneration() throws Exception {
		Collection<Class<?>> classes = Arrays.asList(getEntityClasses());
		DataGenerationService service = new DataGenerationServiceImpl(classes, hive);
		int partitionKeyCount = 2;
		int instanceCount = 4;
		
		Collection<Long> ids = service.generate(WeatherReport.class.getName(), partitionKeyCount, instanceCount);
		assertEquals(partitionKeyCount*instanceCount, ids.size());
		
		DataAccessObject<Object, Serializable> dao = 
			new BaseDataAccessObject(ConfigurationReader.readConfiguration(WeatherReport.class), hive, factory);
		
		Collection<Object> instances = Lists.newArrayList();
		for(Long id : ids) {
			Object instance = dao.get(Integer.valueOf(id.toString()));
			assertNotNull(instance);
			instances.add(instance);
		}
		final EntityConfig entityConfig = config.getEntityConfig(WeatherReport.class);
		assertEquals(partitionKeyCount, Filter.grepUnique(new Unary<Object, Object>(){
			public Object f(Object item) {
				return entityConfig.getPrimaryIndexKey(item);
			}}, instances).size());
		assertEquals(partitionKeyCount*instanceCount, Filter.grepUnique(new Unary<Object, Object>(){
			public Object f(Object item) {
				return entityConfig.getId(item);
			}}, instances).size());
	}
	
	@Test
	public void overTheWire() throws Exception {
		Collection<Class<?>> classes = Arrays.asList(getEntityClasses());
		DataGenerationService service = new DataGenerationServiceImpl(classes, hive);
		JaxWsServerFactoryBean sf = new JaxWsServerFactoryBean();
		sf.setAddress("local://generate");
		sf.setServiceBean(service);
		sf.setServiceClass(DataGenerationService.class);
		sf.setDataBinding(new AegisDatabinding());
		sf.getServiceFactory().setDataBinding(new AegisDatabinding());
		sf.create().start();
		
		JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
		factory.setServiceClass(DataGenerationService.class);
		factory.setAddress("local://generate");
		factory.setDataBinding(new AegisDatabinding());
		factory.getServiceFactory().setDataBinding(new AegisDatabinding());
		DataGenerationService client = (DataGenerationService)factory.create();
		
		int partitionKeyCount = 2;
		int instanceCount = 4;
		
		Collection<Long> ids = client.generate(WeatherReport.class.getName(), partitionKeyCount, instanceCount);
		assertEquals(partitionKeyCount*instanceCount, ids.size());
		for(Long io : ids) {
			assertNotNull(io);
			System.out.println(io + " " + io.getClass().getName());
		}
	}
	
	@BeforeMethod
	public void setup() throws Exception {
		super.beforeMethod();
		new HiveInstaller(getConnectString(getHiveDatabaseName())).run();	
		ConfigurationReader reader = new ConfigurationReader(getEntityClasses());
		reader.install(getConnectString(getHiveDatabaseName()));
		hive = getHive();
		for(String nodeName : getDataNodeNames())
			try {
				hive.addNode(new Node(nodeName, nodeName, "" , HiveDbDialect.H2));
				for(Schema s : getSchemata())
					s.install(getConnectString(nodeName));
			} catch (HiveReadOnlyException e) {
				throw new HiveRuntimeException("Hive was read-only", e);
			}
		config = reader.getHiveConfiguration();
		factory = getSessionFactory();
	}
	
	@BeforeClass
	public void initializeSOAPLocalTransport() throws BusException {		
		String[] transports = new String[]{
				"http://cxf.apache.org/transports/local",
				"http://schemas.xmlsoap.org/soap/http",
				"http://schemas.xmlsoap.org/wsdl/soap/http"
			};
			LocalTransportFactory local = new LocalTransportFactory();
			local.setTransportIds(Arrays.asList(transports));
			
			Bus bus = BusFactory.newInstance().createBus();
			SoapBindingFactory bindingFactory = new SoapBindingFactory();
			bindingFactory.setBus(bus);
			bus.getExtension(BindingFactoryManager.class).registerBindingFactory("http://schemas.xmlsoap.org/wsdl/soap/", bindingFactory);
			bus.getExtension(BindingFactoryManager.class).registerBindingFactory("http://schemas.xmlsoap.org/wsdl/soap/http", bindingFactory);
			
			DestinationFactoryManager dfm = bus.getExtension(DestinationFactoryManager.class);
			
			SoapTransportFactory soap = new SoapTransportFactory();
			soap.setBus(bus);

			dfm.registerDestinationFactory("http://schemas.xmlsoap.org/wsdl/soap/", soap);
			dfm.registerDestinationFactory("http://schemas.xmlsoap.org/soap/", soap);
			dfm.registerDestinationFactory("http://cxf.apache.org/transports/local", soap);
			
			LocalTransportFactory localTransport = new LocalTransportFactory();
			dfm.registerDestinationFactory("http://schemas.xmlsoap.org/soap/http", localTransport);
			dfm.registerDestinationFactory("http://schemas.xmlsoap.org/wsdl/soap/http", localTransport);
			dfm.registerDestinationFactory("http://cxf.apache.org/bindings/xformat", localTransport);
			dfm.registerDestinationFactory("http://cxf.apache.org/transports/local", localTransport);

			ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
			extension.registerConduitInitiator(LocalTransportFactory.TRANSPORT_ID, localTransport);
			extension.registerConduitInitiator("http://schemas.xmlsoap.org/wsdl/soap/", localTransport);
			extension.registerConduitInitiator("http://schemas.xmlsoap.org/soap/http", localTransport);
			extension.registerConduitInitiator("http://schemas.xmlsoap.org/soap/", localTransport);
			
			WSDLManagerImpl manager = new WSDLManagerImpl();
			manager.setBus(bus);
			bus.setExtension(manager, WSDLManager.class);
	}
	
	private Collection<Schema> getSchemata() {
		return Arrays.asList(new Schema[]{
				new WeatherSchema()
		});
	}
	
	private Class<?>[] getEntityClasses() {
		return new Class<?>[]{WeatherReport.class, WeatherEvent.class, Temperature.class};
	}

	@Override
	public Collection<String> getDatabaseNames() {
		Collection<String> dbs = Lists.newArrayList();
		dbs.addAll(getDataNodeNames());
		if(!dbs.contains(getHiveDatabaseName()))
			dbs.add(getHiveDatabaseName());
		return dbs;
	}

	private Collection<String> getDataNodeNames() {
		return Collections.singletonList(getHiveDatabaseName());
	}

	public String getHiveDatabaseName() {
		return "hive";
	}

	private Hive getHive() {
		return Hive.load(getConnectString(getHiveDatabaseName()));
	}
	
	private HiveSessionFactory getSessionFactory() {
		return new HiveSessionFactoryBuilderImpl(
				getConnectString(getHiveDatabaseName()), 
				Arrays.asList(getEntityClasses()), 
				new SequentialShardAccessStrategy());
	}
	
	
}
