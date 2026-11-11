package com.devteria.post.mapper;

import org.mapstruct.Mapper;

import com.devteria.post.dto.response.PostResponse;
import com.devteria.post.entity.Post;

@Mapper(componentModel = "spring")
public interface PostMapper {
    PostResponse toPostResponse(Post post);
}
