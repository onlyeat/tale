package com.tale.controller.admin;

import com.blade.ioc.annotation.Inject;
import com.blade.jdbc.core.Take;
import com.blade.jdbc.model.Paginator;
import com.blade.kit.StringKit;
import com.blade.mvc.annotation.*;
import com.blade.mvc.http.Request;
import com.blade.mvc.ui.RestResponse;
import com.tale.controller.BaseController;
import com.tale.dto.Types;
import com.tale.exception.TipException;
import com.tale.model.Comments;
import com.tale.model.Users;
import com.tale.service.CommentsService;
import com.tale.service.SiteService;
import com.tale.utils.TaleUtils;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 评论管理
 *
 * Created by biezhi on 2017/2/26.
 */
@Slf4j
@Path("admin/comments")
public class CommentController extends BaseController {

    @Inject
    private CommentsService commentsService;

    @Inject
    private SiteService siteService;

    @GetRoute(value = "")
    public String index(@QueryParam(defaultValue = "1") int page,
                        @QueryParam(defaultValue = "15") int limit, Request request) {
        Users users = this.user();
        Paginator<Comments> commentsPaginator = commentsService.getComments(new Take(Comments.class).notEq("author_id", users.getUid()).page(page, limit, "coid desc"));
        request.attribute("comments", commentsPaginator);
        return "admin/comment_list";
    }

    /**
     * 删除一条评论
     * @param coid
     * @return
     */
    @PostRoute(value = "delete")
    @JSON
    public RestResponse delete(@QueryParam Integer coid) {
        try {
            Comments comments = commentsService.byId(coid);
            if(null == comments){
                return RestResponse.fail("不存在该评论");
            }
            commentsService.delete(coid, comments.getCid());
            siteService.cleanCache(Types.C_STATISTICS);
        } catch (Exception e) {
            String msg = "评论删除失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
        return RestResponse.ok();
    }

    @PostRoute(value = "status")
    @JSON
    public RestResponse delete(@QueryParam Integer coid, @QueryParam String status) {
        try {
            Comments comments = new Comments();
            comments.setCoid(coid);
            comments.setStatus(status);
            commentsService.update(comments);
            siteService.cleanCache(Types.C_STATISTICS);
        } catch (Exception e) {
            String msg = "操作失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
        return RestResponse.ok();
    }

    @PostRoute(value = "")
    @JSON
    public RestResponse reply(@QueryParam Integer coid, @QueryParam String content, Request request) {
        if(null == coid || StringKit.isBlank(content)){
            return RestResponse.fail("请输入完整后评论");
        }

        if(content.length() > 2000){
            return RestResponse.fail("请输入2000个字符以内的回复");
        }
        Comments c = commentsService.byId(coid);
        if(null == c){
            return RestResponse.fail("不存在该评论");
        }
        Users users = this.user();
        content = TaleUtils.cleanXSS(content);
        content = EmojiParser.parseToAliases(content);

        Comments comments = new Comments();
        comments.setAuthor(users.getUsername());
        comments.setAuthor_id(users.getUid());
        comments.setCid(c.getCid());
        comments.setIp(request.address());
        comments.setUrl(users.getHome_url());
        comments.setContent(content);
        if(StringKit.isNotBlank(users.getEmail())){
            comments.setMail(users.getEmail());
        } else {
            comments.setMail("support@tale.me");
        }
        comments.setParent(coid);
        try {
            commentsService.saveComment(comments);
            siteService.cleanCache(Types.C_STATISTICS);
            return RestResponse.ok();
        } catch (Exception e) {
            String msg = "回复失败";
            if (e instanceof TipException) {
                msg = e.getMessage();
            } else {
                log.error(msg, e);
            }
            return RestResponse.fail(msg);
        }
    }

}
