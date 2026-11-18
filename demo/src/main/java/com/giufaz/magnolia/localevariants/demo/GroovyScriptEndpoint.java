package com.giufaz.magnolia.localevariants.demo;

import info.magnolia.rest.AbstractEndpoint;
import info.magnolia.rest.registry.ConfiguredEndpointDefinition;

import java.io.PrintWriter;
import java.io.StringWriter;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Demo and test driver only, never on a production instance: runs a Groovy script posted as text/plain to
 * {@code /.rest/lv-demo/groovy} in the request's Magnolia context (the authenticated user).
 * The integration tests use it to create content with the module's own components.
 */
@Path("/lv-demo")
public class GroovyScriptEndpoint extends AbstractEndpoint<ConfiguredEndpointDefinition> {

    @Inject
    public GroovyScriptEndpoint(ConfiguredEndpointDefinition definition) {
        super(definition);
    }

    // The script writes to "out"; a non-null result is appended as "=> result"
    @POST
    @Path("/groovy")
    @Consumes({ MediaType.TEXT_PLAIN, MediaType.WILDCARD })
    @Produces(MediaType.TEXT_PLAIN)
    public String run(String script) {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        Binding binding = new Binding();
        binding.setProperty("out", writer);
        try {
            Object result = new GroovyShell(getClass().getClassLoader(), binding).evaluate(script);
            if (result != null) {
                writer.println("=> " + result);
            }
        } catch (Throwable t) {
            writer.println("EXCEPTION: " + t);
            t.printStackTrace(writer);
        }
        writer.flush();
        return out.toString();
    }
}
