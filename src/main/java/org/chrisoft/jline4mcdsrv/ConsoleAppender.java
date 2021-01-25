package org.chrisoft.jline4mcdsrv;

import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jline.reader.LineReader;

public class ConsoleAppender extends AbstractAppender {

    protected LineReader lr;
    protected RewritePolicy policy;

    public ConsoleAppender(LineReader lr) {
        super("Console", null, PatternLayout.newBuilder().withPattern(JLineForMcDSrvMain.config.logPattern).build(), false);
        this.lr = lr;
    }

    public void setRewritePolicy(RewritePolicy policy) {
        this.policy = policy;
    }

    @Override
    public void append(LogEvent event) {
        if (policy != null)
            event = policy.rewrite(event);

        if (lr.isReading())
            lr.callWidget(lr.CLEAR);

        lr.getTerminal().writer().print(getLayout().toSerializable(event).toString());

        if (lr.isReading()) {
            lr.callWidget(lr.REDRAW_LINE);
            lr.callWidget(lr.REDISPLAY);
        }
        lr.getTerminal().writer().flush();
    }
}
